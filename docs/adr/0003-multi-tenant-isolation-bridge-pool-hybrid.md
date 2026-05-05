# ADR-0003: Multi-tenant isolation — Bridge + Pool hybrid

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: Architecture team, security

## Context

Several dozen large-enterprise tenants. The product is sold as a SaaS but tenants expect data isolation comparable to dedicated deployments. Cost matters — running one Aurora cluster per tenant is not viable. We must also support cross-tenant users (parent-company administrators with read access to subsidiary tenants).

## Decision

**Hybrid isolation:**

- **Application/business databases** (Inventory Core, Master Data, the four sub-system services) use the **Bridge model**: a shared Aurora cluster with one PostgreSQL schema per tenant. Tenant context is established by setting `search_path` at connection acquisition.
- **Common-base databases** (Identity & Tenant, Notification, Workflow, Integration Hub config) use the **Pool model**: a single shared schema with a `tenant_id` column on every table, enforced by Postgres Row-Level Security policies.

## Consequences

**Positive.** Bridge gives us hard schema-level data isolation for the bulk of business data without provisioning dozens of clusters. Pool keeps the common-base lightweight where data volume per tenant is small. Cross-tenant users are served by issuing per-tenant JWTs (one JWT = one tenant scope) — the user picks a tenant after login and a new JWT is issued; this works cleanly with both isolation models.

**Negative.** PostgreSQL has a practical ceiling around several hundred schemas; with several dozen tenants × business services we stay well within bounds, but if tenant count grows by 10× we revisit this ADR. Schema migrations must be applied to every tenant schema — Flyway is configured to iterate. The MyBatis tenant interceptor (in `commons-tenant`) is on the critical path; bugs there leak data across tenants.

**Neutral.** Operations must keep two mental models. Documented in `commons-tenant`.

## Alternatives considered

### Option 1: Pool everywhere
Single schema, `tenant_id` everywhere, RLS. Rejected because enterprise customers explicitly expect schema-level isolation for their inventory data. Also, RLS bugs are catastrophic at this scale.

### Option 2: Silo (one cluster per tenant)
Strongest isolation, highest cost. Rejected on cost — several dozen Aurora clusters dominates the AWS bill and operational toil. May be offered as a premium tier later.

### Option 3: Bridge everywhere
Schema per tenant for everything. Rejected for common-base data because schema multiplication adds no value where data volume per tenant is small.

## References

- `memory/architecture.md` — A3 multi-tenant strategy
- `memory/design_implementation.md` — MyBatis tenant interceptor (in `commons-tenant`)
- ADR-0009 (MyBatis choice)
