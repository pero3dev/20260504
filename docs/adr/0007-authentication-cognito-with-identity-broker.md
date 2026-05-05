# ADR-0007: Authentication — Cognito + custom Identity Broker

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: Architecture, Security

## Context

Several dozen enterprise tenants, each with their own SAML/OIDC IdP (Azure AD, Okta, Google Workspace). Users may belong to multiple tenants. Authorization is scoped by tenant, location, and partner, with step-up MFA required for sensitive operations (inventory adjustments, permission changes). We want managed authentication infrastructure to avoid running Keycloak, but Cognito alone cannot model "this user has access to multiple tenants with different scope sets per tenant."

## Decision

Use **Amazon Cognito** as a user pool and SAML federation engine only. Place a custom **Identity Broker** service in front of Cognito that handles tenant resolution, JWT enrichment, scope computation, tenant switching, and step-up MFA enforcement. Use a single shared Cognito user pool for all tenants (federated to per-tenant SAML IdPs).

The JWT issued by the Identity Broker contains:
- `sub`: user ID
- `tenant_id`: the currently selected tenant (one tenant per JWT)
- `roles`: role names within that tenant
- `scopes.locations[]`, `scopes.partners[]`: data-scope filters
- `mfa_strength`: `low` or `high` — step-up gate
- `exp`: 15 minutes (refresh token issued separately)

Step-up MFA is implemented via a `@RequireStepUp` annotation in user-facing services. When `mfa_strength=low` hits a step-up-protected operation, the service returns 401 with a `WWW-Authenticate` header instructing the frontend to trigger Cognito MFA, after which the Identity Broker reissues a JWT with `mfa_strength=high`.

## Consequences

**Positive.** Cognito does what it is good at (user pool, SAML federation). All multi-tenant business logic lives in our code where we can debug and evolve it. JWT is short-lived (15 min), limiting blast radius of token theft.

**Negative.** Identity Broker is on the hot login path for every user — its availability affects the platform login SLO. Two cognito-side configs per IdP (one per tenant SAML provider) need lifecycle management, automated via Terraform.

**Neutral.** Cross-tenant users need a tenant-picker UI after sign-in, which is built as part of the platform shell.

## Alternatives considered

### Option 1: Cognito only
Use Cognito groups/custom-attributes for tenant and scope. Rejected because Cognito's data model gets unwieldy with several dozen tenants × dynamic per-tenant scope sets — the result is large numbers of Lambda triggers and brittle custom attributes.

### Option 2: Auth0 / Okta
Better multi-tenancy support out of the box. Rejected on cost at this user volume and to keep the auth dependency inside our AWS account boundary.

### Option 3: Keycloak self-managed
Most flexible. Rejected because the platform team is small relative to the 50-engineer overall headcount, and we don't want to run Keycloak HA on top of running EKS.

## References

- `memory/architecture.md` — C2 Identity Broker
- `memory/design_implementation.md` — F1 authorization implementation
