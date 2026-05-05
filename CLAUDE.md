# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state (2026-05-04)

This repo is a **greenfield project**. Requirements gathering is complete (business / technical / architecture / design-implementation policy all closed in the 2026-05-04 dialogue), but no source code, build files, or infrastructure exist yet.

**Detailed decisions live in memory files**, not in this repo. Read these before making any implementation decision:

- `C:\Users\81906\.claude\projects\C--dev-java-20260504\MEMORY.md` — index
- `memory/project_overview.md` — scope, scale, top-3 must requirements
- `memory/business_requirements.md` — 4 sub-systems, audit, integrations, SLAs
- `memory/technical_requirements.md` — full stack
- `memory/architecture.md` — bounded contexts, CQRS scope, multi-tenant strategy, EKS topology
- `memory/design_implementation.md` — DDD/Hexagonal rules, MyBatis conventions, Git/PR/test policy

If a decision is in the memory files, **prefer it over assumptions from training data** — several choices below are deliberate departures from common defaults.

## Big picture

Multi-tenant SaaS for inventory management, sold to dozens of large enterprises. Four sub-systems (**Retail/EC, Manufacturing, 3PL, Wholesale**) share a common backbone but appear as separate products to users. Scale targets: 1M SKUs, 100 sites, 10K concurrent users, **100M inventory transactions/day** (peak ~11,600 TPS).

13 services + 4 BFFs + 4 web UIs:

- Common contexts: Identity & Tenant, Master Data, **Inventory Core** (write authority), **Inventory Read Model** (Redis), Audit, Notification, Workflow, Integration Hub, Analytics
- Per-business contexts: Retail/EC, Manufacturing, 3PL, Wholesale (each owns business logic + API)
- Frontend: 4 GraphQL BFFs, monorepo (Nx/Turborepo) with React UIs

Read/write split is mandatory: <1s inventory reflect at 11.6k TPS isn't achievable with synchronous writes. **Inventory Core publishes events → Read Model (Redis) consumes → screens and external integrations read from Read Model.** CQRS applies *only* to Inventory Core ↔ Read Model — do not retrofit it onto other services.

## Non-obvious decisions (easy to get wrong)

These are deliberate choices that contradict common defaults. **Do not "fix" them without checking memory files first.**

- **MyBatis, not JPA/Hibernate.** No automatic dirty checking. Aggregates persist via explicit `update`. Optimistic locking is `UPDATE ... WHERE version = ?` with manual zero-rows-affected handling.
- **Maven, not Gradle.** Multi-module via parent POM + BOM.
- **No schema registry was originally chosen, then reversed.** Final answer: **AWS Glue Schema Registry is adopted** for all Kafka topics.
- **Audit collection is AOP-only (`@Auditable`), no CDC.** This is a known gap; mitigations are *required*: ArchUnit must enforce `@Auditable` on all DB-mutating operations, Aurora WAL stored separately, DBA actions go through a separate channel. Do not skip these mitigations.
- **PostgreSQL FTS, not OpenSearch.** Acceptable for filter + partial match. If fuzzy / synonyms / relevance ranking become requirements, OpenSearch is added — don't preemptively introduce it.
- **Cognito as user pool + SAML federation only.** All tenant resolution, scope (`locations[]` / `partners[]`), and step-up MFA decisions live in a custom **Identity Broker** service. Do not push tenant logic into Cognito.
- **Multi-tenant DB strategy is hybrid.** App DBs use **Bridge** (shared Aurora cluster, schema per tenant, switch via `SET search_path`). Common-base DBs (Identity / Notification / Workflow) use **Pool** (single DB, `tenant_id` column + RLS). Don't apply the same model to both.
- **DB ownership is logical, not physical.** Roughly 3 Aurora clusters host all services (hot path / business / common). Each service still owns its own database within a cluster — **no cross-service DB reads**, only API or Kafka.
- **No service mesh.** Plain K8s + Spring Cloud LoadBalancer + Datadog APM. Don't introduce Istio/App Mesh without re-deciding.
- **EDI is OSS-only.** AS2 via Apache Camel, EDIFACT via Smooks, distribution-BMS implemented in-house. JCA手順 / 全銀手順 are **out of scope** — confirmed.
- **JCA pen-test is pre-release only**, not continuous. (Item D5-2.)
- **Staging is intentionally smaller than prod.** Compensate with strict Canary on prod (Argo Rollouts: 1% → 5% → 25% → 100% with auto-rollback) and short-lived staging scale-ups (Karpenter) for load tests.

## Architectural rules every service follows

- **Hexagonal (ports & adapters)**, full tactical DDD applied to every service (yes, including light ones — uniformity matters at 50+ engineers).
- Standard package layout: `domain/` (pure Java) → `application/` (`usecase/`, `port/in`, `port/out`) → `adapter/` (`in/rest`, `in/graphql`, `in/kafka`, `out/persistence`, `out/kafka`, `out/external`) → `config/`.
- Single Maven module per service + **ArchUnit enforces layer direction** (domain → application → adapter is one-way; tests fail otherwise).
- Snowflake IDs (custom, time-ordered bigint) for all aggregate IDs. No DB auto-increment, no UUID v4.
- 1 use case = 1 RDB transaction = 1 aggregate boundary. Cross-aggregate consistency uses **Saga**: orchestration inside a business context, choreography for cross-context event chains.
- **Transactional Outbox**: domain events go to an `outbox` table in the same DB transaction as the aggregate write; a separate publisher pumps Kafka. Don't publish to Kafka directly from a use case.
- All emitted Kafka events carry `tenant_id`, `trace_id`, and schema version as required metadata.
- Errors over the wire follow **RFC 7807 (Problem Details)**. Resilience4j handles retry / circuit breaker / bulkhead.
- REST APIs: OpenAPI 3, **schema-first**, URL path versioning (`/v1/...`), **cursor-based pagination only**. Each service's spec lives in `docs/openapi/<service>.yaml`; the openapi-generator-maven-plugin produces server interfaces + DTOs in `generate-sources`, and controllers `implement` those interfaces — drift fails the build. Generated code lives under `target/generated-sources/openapi` (not committed); generated DTOs are in `<service-package>.adapter.in.rest.api.model`. The `ProblemDetail` schema in the spec documents the error contract but is not what the runtime returns — `commons-error.GlobalExceptionHandler` produces Spring's `ProblemDetail` instances. Path versioning lives in spec `paths:` (not server URL), so Spring routing stays simple.
- GraphQL: schema-first (`.graphqls`), **DataLoader required** to avoid N+1.

## Shared library: `inventory-commons`

Day-1 priority. Ten sub-modules (BOM / tenant / persistence / event / audit / error / security / resilience / observability / test) absorb the cross-cutting machinery (MyBatis tenant `search_path` interceptor, optimistic-lock helper, aggregate repository abstract, outbox publisher, `@Auditable` aspect, RFC 7807 standard, Spring Security baseline, Resilience4j defaults, OTel + Datadog setup, Testcontainers config, ArchUnit standard rules). **All 13 services depend on it from day one** — never re-implement these in a service.

`commons-security` provides the entry point + access denied handler + JWT roles converter + `TenantContextFilter` + `PlatformSecurity` helper. Each service has a tiny `SecurityConfig` that calls `platform.applyDefaults(http)` and adds its own `permitAll` paths — that's it. Don't recreate `ProblemAuthenticationEntryPoint` or hand-wire JWT converter in services.

A separate platform team owns this library (Conway's law: the team boundary mirrors the dependency boundary).

## Adding a new service

Don't freestyle. Read [`docs/services/scaffold-guide.md`](./docs/services/scaffold-guide.md) — it has a decision matrix (DB? read/write? Kafka?), POM templates per case, package layout, the `@Auditable` rules, and a list of common pitfalls. The two existing services are reference implementations: `inventory-core` for the DB + Outbox case, `inventory-read-model` for the stateless / CQRS-read case.

## Workflow & release

- **Trunk-Based Development**: short-lived branches (< 1 day), squash-merge to `main`, ≥2 reviewers, PR ≤ 600 lines, automated checks (Lint / Format / Unit / ArchUnit / SAST / image scan) all required.
- **Conventional Commits** (`feat:` / `fix:` / `chore:` / ...).
- Feature toggles via **Unleash (OSS)** — incomplete features can land on `main` behind a flag.
- ADRs in `docs/adr/`, **including rejected proposals**.

## Code comments are written in Japanese

Comments in Java/YAML/XML/SQL/properties files **default to Japanese** — Javadoc, inline comments, TODOs, all of it. Identifiers (class / method / variable / package names) stay in English. ADR numbers and technical terms (CQRS, Bridge model, Outbox) stay in English inside Japanese sentences. Externally published API descriptions (OpenAPI/GraphQL schema docs) are English because integrators may be non-Japanese-speaking.

When editing an existing file with English comments, take the opportunity to convert them.

## Build / test / run

Day-1 scaffolding is in place: aggregator parent POM at the repo root, `inventory-commons` (9 modules) and `services/inventory-core` (vertical spike for the Reserve Inventory use case).

- Full build: `mvn clean verify`
- Run a single test: `mvn -pl <module> -Dtest=<TestClass>#<method> test`
- Format: `mvn spotless:apply`
- Run inventory-core locally: `mvn -pl services/inventory-core spring-boot:run` (requires local Postgres + Kafka — see service `application.yml` for env vars)
- ArchUnit rules: `commons-test` exposes `HexagonalLayerRules`; each service has its own `architecture/ArchitectureTest.java` that imports them.

### Local vs CI test boundaries

`mvn verify` works locally and on CI, but tests that need Docker have a split:

- **Unit / ArchUnit / Spotless / per-module @SpringBootTest** — run anywhere. No Docker dependency.
- **Cross-service E2E (`e2e-tests/`, `services/inventory-core/.../ReservationE2EIntegrationTest`)** — boots multiple Spring contexts in one JVM with Postgres + Kafka + Redis Testcontainers. Annotated `@Testcontainers(disabledWithoutDocker = true)`, so they auto-skip when Docker is absent.

**The cross-service E2E is treated as CI-only by convention.** On Windows + Docker Desktop 4.71+ they are unreliable for two reasons:
1. **Docker Desktop hardened the engine pipe** so direct HTTP from non-CLI clients (Java/Go/Python SDKs) returns 400. Workaround: enable *Settings → General → Expose daemon on `tcp://localhost:2375` without TLS* and run with `DOCKER_HOST=tcp://localhost:2375 DOCKER_API_VERSION=1.43`.
2. **Multi-Spring-context HikariPool starvation** — booting 4 services (identity-broker / inventory-core / inventory-read-model / audit-service) in one JVM puts pressure on Postgres connections; audit emission `REQUIRES_NEW` sub-transactions can fail. CI runners (Linux + abundant FDs) are more forgiving; Windows hosts often deadlock.

Use `mvn -pl '!e2e-tests' verify` for the inner dev loop. CI (`ubuntu-latest` runner with native Docker socket) handles the full reactor including E2E.

For local Kafka path coverage without spinning up the cross-service IT, `services/inventory-core` ships a single-context lite E2E (`KafkaIntegrationE2ETest`) that boots only `inventory-core` with Postgres + Kafka. It covers (a) Reserve → Outbox → Kafka publish, (b) `master.product.v1` → SkuMasterListener → `sku_registry` projection, (c) Reserve with unregistered SKU → 422. Auto-skips when Docker is unavailable. **This single-context IT is the canonical CI Kafka validation** — it runs in surefire (because it ends with `Test.java`) and covers the same architectural concerns at lower fragility.

The cross-service `e2e-tests/*IT.java` are **currently skipped on CI as well** (`<skipITs>true</skipITs>` in the module POM, with no override in CI). Multi-Spring-context resource cleanup across IT classes turned out to be unreliable (Postgres connection rejection, Kafka producer disconnects, JVM hang on shutdown). Treating the multi-context IT as a future hardening target lets us keep CI green while still preserving the test code as a reference implementation. The single-context IT covers the production-relevant Kafka semantics. Rationale + alternatives are captured in [ADR-0014](./docs/adr/0014-cross-service-e2e-deferred-to-local-only.md).

The vertical spike (`services/inventory-core`) is the canonical reference for the package layout, MyBatis + outbox + optimistic-lock conventions, and `@Auditable` placement. Use it as the template when scaffolding the other 12 services.

## CI

Workflows live in `.github/workflows/`:

- `ci.yml` — runs `mvn -B -ntp verify` on PRs and main pushes. This runs unit tests, integration tests (Testcontainers — Docker is provided by the GitHub-hosted runner), ArchUnit, and `spotless:check`. A separate `lint-format` job re-runs `spotless:check` for clarity.
- `codeql.yml` — CodeQL SAST (`security-and-quality` queries). Runs on PRs, main, and weekly.
- `dependabot.yml` — Maven + GitHub Actions dependency updates, weekly Monday 09:00 JST.

If `spotless:check` fails on a PR, run `mvn spotless:apply` locally and commit the diff. Spotless is bound to the `verify` phase in the parent POM, so `mvn verify` will catch formatting issues before push.

`pull_request_template.md` enforces the F5 PR contract (What/Why/How tested/ADR link/Migration). `CODEOWNERS` is a placeholder — team handles need to be filled in once GitHub teams are provisioned.

## When in doubt

1. Re-read the relevant memory file before improvising.
2. If a decision is missing, propose it as an ADR rather than coding-and-asking-forgiveness.
3. The three Must requirements drive tie-breaking: **real-time + auditability + external integration**. Sacrifice elsewhere first.
