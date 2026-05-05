# ADR-0004: Apply CQRS only to Inventory Core ↔ Read Model

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: Architecture team

## Context

The business SLA requires inventory writes to reflect in user-visible reads within 1 second, with an eventual peak of ~11,600 transactions per second. A synchronous "write to Aurora, read from Aurora" pipeline cannot meet this SLA at this scale because Aurora replica lag, connection pressure, and multi-tenant search-path overhead all stack up. CQRS (separating the write authority from a fast read projection) is the standard answer, but it adds operational complexity and is harmful when over-applied.

## Decision

Apply CQRS **only** between Inventory Core (write authority, Aurora) and Inventory Read Model (Redis projection). All other services use plain CRUD with their own DB. Master Data may use a simple read cache when needed but is not CQRS.

## Consequences

**Positive.** The hot path gets its dedicated read store (Redis) sized for read latency, while writes remain on Aurora with full ACID. Other services stay simple — there is no event-sourcing tax on Workflow, Notification, or the sub-system services.

**Negative.** Read Model lag is part of the public contract — clients querying inventory get eventually-consistent data. The acceptable lag is ≤1 second; staying inside that budget requires monitoring (Kafka consumer lag alerts) and capacity planning (Redis throughput).

**Neutral.** Read Model is rebuildable from the Inventory Core event log — no separate backup needed for Redis.

## Alternatives considered

### Option 1: CQRS everywhere
Every service splits write and read. Rejected as massive overkill — Workflow and Notification have neither the write rate nor the read latency requirements that justify it.

### Option 2: Aurora read replicas instead of Redis
Cheaper and simpler. Rejected because (a) replica lag is unbounded under write pressure, (b) Aurora scaling is coarser than Redis, and (c) inventory reads are dominated by point lookups, which Redis serves at sub-millisecond.

### Option 3: Event sourcing on Inventory Core
Inventory state derived from event log. Rejected as over-engineering for this iteration; we keep a current-state table in Aurora and emit events via the Transactional Outbox (ADR-0011-related).

## References

- `memory/business_requirements.md` — <1s inventory reflect SLO
- `memory/architecture.md` — B2 CQRS scope
