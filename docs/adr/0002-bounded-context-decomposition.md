# ADR-0002: Bounded context decomposition

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: Architecture team

## Context

Per ADR-0001, services are sized at the bounded-context level. Users see four separate sub-systems (Retail/EC, Manufacturing, 3PL, Wholesale) but all four share core business concepts: SKU, location, partner, inventory quantity. We need to decide where to draw service boundaries so that (a) sub-systems can evolve independently and (b) shared concepts are owned by exactly one service.

## Decision

Decompose into **13 backend services + 4 BFFs + 4 web UIs**:

**Common-base contexts (shared by all sub-systems):**

1. **Identity & Tenant** — tenant management, identity broker, SAML federation, users/roles
2. **Master Data** — SKU, location, partner, lot, price masters
3. **Inventory Core** — sole authority for inventory state (quantity / location / ownership / reservation), publisher of inventory events
4. **Inventory Read Model** — Redis-backed projection consumed by screens and external integrations
5. **Audit** — audit event collection, hash-chain generation, S3 WORM storage
6. **Notification** — in-app push (WebSocket/SSE), subscription management
7. **Workflow** — approval flows (DB state machine), application/approval/rejection events
8. **Integration Hub** — outer envelope for the eight external integration adapters (split per ADR-0007, see below)
9. **Analytics** — in-app reporting aggregates + Snowflake CDC pipeline

**Per-sub-system contexts (each owns business logic + API):**

10. **Retail/EC**
11. **Manufacturing**
12. **3PL**
13. **Wholesale**

**Frontend:** four BFFs (one per sub-system), four web UIs in a single monorepo.

The Integration Hub itself is further split into one adapter service per external system family (ERP, EC, EDI, POS, WMS, BI pipeline, accounting); see ADR-0007.

## Consequences

**Positive.** Inventory Core is the single write authority — there is no debate about which service "owns" inventory. Sub-system teams can iterate on Retail vs Manufacturing without coordination. Common-base contexts are reused, not duplicated.

**Negative.** A reservation flow that starts in Retail/EC must traverse Retail → Inventory Core → (event) → Inventory Read Model → (event) → Audit → (event) → Integration Hub. Tracing and saga design are non-trivial.

**Neutral.** Each common-base context is also a team — Conway's law applies in our favor here.

## Alternatives considered

### Option 1: Sub-system-only services (no shared "common base")
Each sub-system owns its own copy of Inventory, Master Data, Audit. Rejected because shared concepts (one SKU master, one inventory quantity) are explicit business requirements; duplicating them creates reconciliation work.

### Option 2: Single "Inventory Service" combining Core + Read Model
Rejected because the write authority and the read projection have very different scaling profiles and access patterns; CQRS only makes sense if they are physically separable.

## References

- `memory/business_requirements.md` — sub-system relationship is (b) common base + per-sub-system UI
- `memory/architecture.md` — A2 bounded contexts
- ADR-0004 (CQRS scope)
- ADR-0007 (Integration Hub adapter split)
