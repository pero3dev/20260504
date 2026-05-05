# ADR-0013: EKS topology — single cluster per environment, namespace per service

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: Architecture, Platform

## Context

The team is new to Kubernetes. Operating multiple clusters per environment increases the surface area we must master before production launch. At the same time, we must isolate environments completely and offer per-service blast-radius limits.

## Decision

- **One EKS cluster per environment** (dev / staging / prod — three clusters total).
- **One Kubernetes namespace per service.** Service workload, ConfigMaps, Secrets refs, NetworkPolicies, and RBAC are all namespace-scoped.
- **No service mesh** (no Istio, no App Mesh) for v1. Service-to-service traffic uses plain Kubernetes Services + Spring Cloud LoadBalancer client-side balancing. Datadog APM provides distributed tracing without sidecar overhead.
- **NetworkPolicies** restrict inter-namespace traffic to declared dependencies.
- **IRSA** (IAM Roles for Service Accounts) gives each pod the minimum AWS permissions it needs.
- Deployment is **GitOps via ArgoCD**, with **Argo Rollouts** for canary (1% → 5% → 25% → 100%, automated rollback on SLO regression — Datadog-driven).
- DB schema migrations run as **Flyway in a dedicated Kubernetes Job**, scheduled to complete before the application Rollout begins.

A separate **SRE / Platform team** owns the cluster, the GitOps pipeline, and the `inventory-commons` library. External support (AWS ProServe or equivalent) is engaged through initial production launch to compensate for the team's k8s inexperience.

## Consequences

**Positive.** Operational complexity is bounded — three clusters, predictable namespace layout, no service-mesh debugging. Canary rollouts give us automatic protection against bad deploys, partially compensating for staging being deliberately smaller than production. IRSA keeps blast radius narrow at the IAM layer.

**Negative.** A cluster-level outage takes down a whole environment. Acceptable at 99.9% SLO (8.76 hours/year downtime budget) and mitigated by multi-AZ node groups, EKS managed control plane, and disaster recovery via Aurora PITR.

**Neutral.** Future migration to a service mesh (when traffic policies, mTLS, or per-route SLO enforcement become valuable) is feasible but not planned.

## Alternatives considered

### Option 1: Multiple clusters per environment (one per service group)
Stronger blast-radius isolation. Rejected — operational load is multiplied while we are still learning k8s.

### Option 2: Service mesh (Istio or App Mesh)
Powerful traffic management. Rejected for v1 on operational cost; revisit in v2.

### Option 3: ECS Fargate instead of EKS
Considered seriously. Rejected because EKS gives us the broader Kubernetes ecosystem (Argo, Karpenter, etc.) and the Platform team is being staffed to support it.

## References

- `memory/architecture.md` — C4 EKS topology, D1 CI/CD, D2 DR
