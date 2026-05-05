# ADR-0006: API style — REST for external, GraphQL for BFF

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: Architecture, Frontend

## Context

We have two distinct API consumer profiles. External integrations (ERP, EC, EDI, etc.) need a stable, machine-friendly contract that works with off-the-shelf integration tooling. Internal screens — four sub-system UIs — need flexible, low-round-trip queries with type-safe client code generation.

## Decision

**REST + OpenAPI 3 for externally exposed APIs and Integration Hub.**
- Schema-first: the OpenAPI YAML is the source of truth, server stubs and TypeScript clients are generated.
- URL-path versioning (`/v1/...`).
- Cursor-based pagination only — offset pagination is forbidden.
- Errors follow RFC 7807 (Problem Details for HTTP APIs).

**GraphQL for the BFF layer (one BFF per sub-system).**
- Schema-first (`.graphqls`).
- DataLoader is mandatory at the BFF layer to prevent N+1 fan-out.
- Authorization is enforced at both schema directives (cheap rejection) and resolver execution (data-driven checks).

## Consequences

**Positive.** External integrators get an artifact (OpenAPI) that every commercial iPaaS and ERP middleware understands. Frontend developers get GraphQL ergonomics without exposing it to outsiders. The two surfaces evolve at different speeds — internal GraphQL can iterate freely, external REST is treated as a stable contract.

**Negative.** Two API styles, two skill sets, two doc generators. Maintaining the OpenAPI contract takes discipline (no implementation-driven drift). GraphQL introduces N+1 risk that DataLoader must catch — added to ArchUnit/lint checks.

## Alternatives considered

### Option 1: GraphQL for everything (including external)
Rejected — many external systems and EDI middleware do not speak GraphQL. ERPs almost universally consume REST/SOAP.

### Option 2: REST for everything (including BFF)
Rejected — the four sub-system UIs would need separate endpoints per view and would suffer round-trip overhead. GraphQL's request-shape flexibility is a real productivity win for screen development.

### Option 3: gRPC internally
Considered for service-to-service. Deferred — REST internal calls plus Kafka events are sufficient for v1; gRPC may be revisited if intra-cluster latency becomes a bottleneck.

## References

- `memory/architecture.md` — B3 API gateway and BFF
- `memory/design_implementation.md` — E5 API schema design
