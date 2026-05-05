# ADR-0001: Adopt mid-grain microservices

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: Architecture team

## Context

We are building a multi-tenant SaaS for inventory management at Tier-1 enterprise scale: 100M inventory transactions/day (~11,600 TPS peak), 10,000 concurrent users, 4 distinct business sub-systems sharing common business concepts, and a development team of 50+ engineers across multiple sub-teams releasing weekly or more frequently. The system must integrate with eight external system categories (ERP, accounting, EC, POS, WMS/TMS, EDI, BI/DWH, SSO) in seconds-level real time.

## Decision

Adopt **mid-grain microservices** sized at the bounded-context level. Each service owns its own database, its own deployment lifecycle, and its own team. Inter-service communication is REST/GraphQL where synchronous, Kafka where asynchronous.

## Consequences

**Positive.** Independent team release cadence (compatible with weekly+ releases at 50+ engineers). Independent scaling of hot paths (Inventory Core can run more replicas than Workflow). Failure isolation between business sub-systems. Natural alignment with CQRS and event-driven patterns required by the <1s inventory-reflect SLO.

**Negative.** Distributed-system complexity (saga, eventual consistency, cross-service tracing). Higher operational baseline (Datadog, Kafka, multiple DB clusters). Kubernetes expertise becomes critical despite the team being new to k8s — mitigated by a dedicated SRE/Platform team and external support (see ADR-0013).

**Neutral.** Forces investment in shared libraries (`inventory-commons`) so 13 services do not re-invent cross-cutting concerns.

## Alternatives considered

### Option 1: Modular monolith
Single deployable, internal modules. Rejected because a 50-engineer team merging into a single deployable cannot sustain weekly release cadence; merge contention and release-train coordination becomes the bottleneck.

### Option 2: Fine-grained microservices (Netflix-style)
Many small services per business capability. Rejected because the team is new to k8s — fine-grained adds operational surface area we cannot absorb. Inter-service-call fan-out also threatens the <1s reflect SLO.

### Option 3: Hybrid (mid-grain core + serverless edges)
Mid-grain microservices for core, AWS Lambda for adapters/notifications. Rejected for v1 to avoid mixing operational paradigms while the team is still building k8s muscle. May be revisited for the Integration Hub adapters.

## References

- `memory/architecture.md` — A1 architecture style decision
- ADR-0002 (bounded context decomposition)
- ADR-0013 (EKS topology)
