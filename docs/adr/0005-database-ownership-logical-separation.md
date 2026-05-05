# ADR-0005: Database ownership — logical separation across shared Aurora clusters

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: Architecture, Platform

## Context

A strict "database per service" interpretation of microservices would mean one Aurora cluster per service: roughly 8–9 clusters. Each Aurora cluster is a non-trivial cost line and operational burden (parameter groups, scaling, patching, monitoring). On the other side, putting all services on one cluster makes that cluster a single point of failure for the whole platform.

## Decision

Run **three Aurora clusters** grouped by access pattern, with one logical database per service inside the appropriate cluster:

- **Aurora-A (hot path):** Inventory Core, Master Data
- **Aurora-B (sub-systems):** Retail/EC, Manufacturing, 3PL, Wholesale
- **Aurora-C (common base):** Identity & Tenant, Notification, Workflow, Integration Hub config

Each service owns its database. **No cross-service DB reads are allowed under any circumstance** — services communicate through APIs or Kafka events. Co-location is purely an operational convenience.

## Consequences

**Positive.** Three clusters instead of nine cuts cost and operational load. Hot-path workloads are isolated from sub-system batches and from low-traffic common-base CRUD. Tuning (instance class, parameter group, IO config) is grouped by similar workload.

**Negative.** Aurora-A's cluster-level outage takes down both Inventory Core and Master Data — they share blast radius. Acceptable because both are needed for the platform to function regardless. Aurora-B's outage stops all four sub-systems but the common base (auth, audit) keeps running, so users see a graceful degradation.

**Neutral.** Per-service DBs inside the cluster are accessed via service-specific DB users with privileges scoped to their own database — defense in depth against accidental cross-service queries.

## Alternatives considered

### Option 1: One Aurora cluster per service (~9 clusters)
Strongest isolation. Rejected on cost and operations toil.

### Option 2: One Aurora cluster total
Simplest. Rejected because a single cluster outage = full platform outage, and tuning cannot be optimized for both hot path and batch workloads simultaneously.

## References

- `memory/architecture.md` — B4 DB ownership model
- ADR-0003 (multi-tenant isolation, Bridge model lives inside these clusters)
