# ADR-0023: Immediate token revocation via per-user Redis revocation list

- **Status**: Accepted
- **Date**: 2026-05-10
- **Deciders**: Security, Identity Broker, Platform

## Context

Identity Broker issues short-lived JWT access tokens (15-minute TTL, ADR-0007). The platform completed several offboarding operations during the A5 follow-up series:

- `POST /v1/admin/tenants/{id}/deactivate` (A5 follow-up¹) blocks new tenant-session issuance
- `DELETE /v1/admin/users/{id}/memberships/{tenantId}` (¹⁴) removes a single membership
- `POST /v1/admin/users/{id}/deactivate` (¹⁵) blocks all future logins

In every case, **already-issued access tokens remain valid until their TTL expires** because verification is stateless (signature + `exp` only). A 15-minute window between admin action and token expiry is acceptable for tenant-deactivation and membership-removal scenarios but unacceptable for incident response: a compromised user, a leaked token, or a fired employee with active session must be cut off **immediately**, not within 15 minutes.

The downstream services (12 of them) verify tokens locally without round-tripping to Identity Broker. Any revocation mechanism must be cheap to consult on every authenticated request — adding a database round-trip on the hot path would multiply DB load by the request rate (peak 11.6k TPS, ADR-0001).

The platform already runs Redis for `inventory-read-model` projections, so introducing Redis for revocation reuses operational expertise.

## Decision

Add a **per-user revocation list in Redis**, keyed by user ID, populated on every offboarding operation, and consulted by a new `RevocationCheckFilter` registered after `BearerTokenAuthenticationFilter` in `commons-security`.

### Mechanics

1. **Key scheme.** `revocation:user:<userId>` → value is `revoked_at` (Instant ISO string for ops debugging). TTL = access token TTL (15 minutes, matching `NimbusJwtTokenIssuer.ACCESS_TOKEN_TTL`). After expiry the entry self-deletes; no GC job needed.

2. **Granularity = per-user.** Single key per user covers all current triggers:
   - User deactivate → revoke user
   - Tenant deactivate → for each membership of that tenant, revoke the user
   - Membership remove → revoke just that user (they may still have access to other tenants, but their *current* access token has roles for the removed tenant baked in; safer to invalidate and let them re-authenticate to get a fresh token without those roles)
   - Role change (future) → revoke user

   We deliberately do NOT use `jti` (per-token) revocation: it would require a write per token issued (high write rate), and a single user typically has a small number of in-flight tokens (one session, one tenant access at a time).

3. **Filter contract.** A new `RevocationCheckFilter` in `commons-security`:
   - Runs after `BearerTokenAuthenticationFilter` populates the JWT principal
   - Looks up `revocation:user:<sub>` in Redis (single GET, ~0.5ms p99 in cluster)
   - If present, sets `SecurityContextHolder` to null and lets `ProblemAuthenticationEntryPoint` return 401 with `errorCode=ERR_TOKEN_REVOKED`
   - Cache miss = let the request through (fail-open on Redis outage; see Consequences)

4. **Identity Broker integration.** The 4 offboarding paths gain a new outbound port `RevocationListPublisher.revoke(userId)`:
   - `DeactivateTenantUseCase` (after status update) → fetch all `tenant_memberships.user_id` for the tenant → revoke each
   - `DeactivateUserUseCase` → revoke the single user
   - `RemoveUserMembershipUseCase` → revoke the user
   - Future role-change → revoke the user

5. **Schema versioning.** Key prefix `revocation:user:` is a stable contract; if we later add per-tenant or per-jti revocation, those go under different prefixes (`revocation:tenant:`, `revocation:jti:`) so the user filter is not affected.

6. **Failure mode.** Redis unavailable on the publisher side → admin operation succeeds (DB row updated) but revocation is not propagated. The mitigations: (a) Resilience4j retry on the publisher (3 attempts, exponential backoff), (b) operator-visible warning log + Datadog alert, (c) audit trail captures the admin action regardless via `@Auditable`. The 15-minute natural expiry is the ultimate fallback.

## Consequences

**Positive.**

- Closes the J-SOX incident-response gap that A5 follow-up¹ / ¹⁴ / ¹⁵ explicitly noted.
- Single Redis GET per request (0.5ms typical) is cheaper than the alternatives.
- Per-user granularity covers all current triggers without the write overhead of per-jti.
- Auto-expiring entries mean the revocation list stays bounded in size — at most `users * tokens-in-flight` rows, with TTL = 15 min.
- Consumers (12 services) only depend on `commons-security`, which is the natural place for this filter — no per-service code change.

**Negative.**

- Adds Redis to the critical path of every authenticated request. Redis outage → fail-open (request goes through) is a deliberate weakness: we prioritize availability over freshness. The alternative (fail-closed) would mean a Redis outage = full platform login outage, which is too aggressive for a *defense-in-depth* mitigation that already has a 15-minute fallback (TTL).
- Tokens issued *during* the moment of revocation might still be valid for up to one Redis replication lag (~ms in same-AZ replication). For physical certainty we would need synchronous fencing, which is overkill for the 15-minute baseline.
- 12 services now depend on Redis being reachable from the EKS cluster (it already is for `inventory-read-model`), but each service's `commons-security` will need a Redis client config. Health-check should distinguish "Redis warm but slow" from "Redis cold" so that we don't false-positive degrade login.

**Neutral.**

- Datadog dashboards need a new metric (`revocation_filter.cache_hit_ratio`) and an alert on Redis miss rate, which is the same pattern as inventory-read-model's Redis dashboard — no new tooling.
- Memory cost: ~50 bytes per revoked user × at most N users in 15-min sliding window. For 10k users at peak with hourly offboarding events, this is negligible (< 1 MB).

## Alternatives considered

### Option 1: Database-backed revocation list

A `revoked_tokens` table in the identity-broker DB, queried by every service via a new REST endpoint or shared replica.

Rejected because: cross-service DB read violates ADR-0005 (no cross-service DB); the REST endpoint would put login-path load on identity-broker (which is already the auth bottleneck); per-request DB hit at 11.6k TPS would saturate the DB long before it saturates the platform.

### Option 2: Per-token (jti) revocation list

Issue every access token with a unique `jti`, store the jti in Redis on issuance, mark it revoked on logout/offboarding. Filter checks `revocation:jti:<jti>`.

Rejected because: requires a write *per token issued* (peak 11.6k TPS = 11.6k Redis writes/sec just for issuance), which is 100× the offboarding event rate; tokens-in-flight are not interesting individually — what we care about is *the user* losing access. Per-jti also doesn't clean up automatically on revocation (need separate GC) unless we set TTL = token TTL, in which case it's essentially "remember every issued token for 15 min" which dominates revocation traffic.

### Option 3: Reduce access token TTL to 1 minute

If TTL is 1 minute, an offboarded user's tokens become invalid within 1 minute without any revocation list. Trade-off: token refresh rate goes up 15×, putting more load on identity-broker.

Rejected because: 15-minute incident-response window is unacceptable for the Must requirement (ADR-0001 *real-time + auditability*); 1-minute window is also unacceptable but for a different reason — peak 11.6k TPS × refresh-every-1min = ~190 refreshes/sec just from active sessions, and that's against an identity-broker that is also handling new logins. The per-user revocation list has a fixed cost regardless of session count.

### Option 4: Trust the 15-minute TTL — do nothing

Accept the 15-minute exposure window. Mitigations: shorten TTL slightly (to 10 min), enforce strong logout flows in web apps, add ALB-level IP allowlist for admin paths.

Rejected because: 15 minutes is too long for an active incident (compromised credentials or rogue insider). Customer compliance requirements (SOX, ISO 27001 follow-up audits) call for "immediate" revocation, with "immediate" historically being interpreted as ≤ 60 seconds in audit findings.

## References

- ADR-0007 (Authentication) — establishes 15-min access token TTL
- ADR-0001 (Mid-grain microservices) — peak 11.6k TPS scale target
- ADR-0005 (DB ownership) — no cross-service DB access
- ADR-0008 (Audit AOP) — `@Auditable` records the admin action
- A5 follow-up¹⁴ / ¹⁵ commit messages (`d21feab`, `3e4fb1a`) — note the deferred revocation as future work
