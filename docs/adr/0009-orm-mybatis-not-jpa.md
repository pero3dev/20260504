# ADR-0009: ORM — MyBatis, not JPA/Hibernate

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: Architecture, Tech leads

## Context

The team is fluent in Spring Boot, and Spring Data JPA + Hibernate is the path of least resistance. However: (a) we have explicit performance budgets at high write rates where Hibernate's session-management overhead is non-trivial, (b) the multi-tenant Bridge model requires switching `search_path` per connection in a way that fights Hibernate's first/second-level cache assumptions, and (c) the team has prior preference for SQL control. MyBatis is widely used in Japanese enterprise Java projects for these reasons.

## Decision

Use **MyBatis** as the SQL mapper across all services. Use **Flyway** for schema migrations (run as a dedicated Kubernetes Job before app rollout, never as part of app startup).

Mandatory conventions, all encoded in `commons-persistence`:

- Optimistic locking: every aggregate has a `version` column. UPDATE statements include `WHERE version = :expectedVersion`. Affected-rows = 0 ⇒ throw `OptimisticLockException`. Helper provided so individual mappers don't reinvent this.
- Aggregate Repository abstract: `load`, `save` (insert-or-update), `delete` template, used by every aggregate.
- Tenant interceptor: at connection acquisition, sets `search_path` from `TenantContext` (Bridge model).
- Domain events: emitted via the Transactional Outbox pattern — written to an `outbox` table in the same DB transaction as the aggregate write. A separate publisher pumps Kafka.

Domain objects are pure POJOs with no MyBatis annotations — mapping XML or annotated mapper interfaces live in the adapter layer only.

## Consequences

**Positive.** SQL is explicit and reviewable, performance is predictable, and the tenant `search_path` interceptor lives at a single, well-defined point. Domain model stays pure (good for the DDD direction taken in ADR-0010-related decisions).

**Negative.** No automatic dirty checking — every aggregate change requires an explicit `repository.save()`. Forgetting it is a class of bug we have to catch in code review and integration tests. Lazy loading is manual; eager fetch joins must be planned.

**Neutral.** Onboarding cost for engineers who only know JPA — mitigated by the abstract `AggregateRepository` template, which makes the common cases trivial.

## Alternatives considered

### Option 1: Spring Data JPA + Hibernate
Default Spring Boot path. Rejected for reasons in Context. Hibernate's multi-tenancy support exists but interacts awkwardly with our Bridge model.

### Option 2: jOOQ
Type-safe SQL DSL. Considered as a complement for complex analytical queries; we may add jOOQ later for reporting code paths but not as the primary persistence layer.

### Option 3: Spring JDBC + JdbcTemplate
Lower-level. Rejected — too much boilerplate at 13 services × dozens of aggregates.

## References

- `memory/design_implementation.md` — E7 standard libraries, MyBatis × DDD × Hexagonal conventions
- `commons-persistence` (skeleton)
