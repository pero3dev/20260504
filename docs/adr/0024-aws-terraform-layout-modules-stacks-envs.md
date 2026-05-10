# ADR-0024: AWS infra Terraform layout — modules / stacks / envs separation

- **Status**: Accepted
- **Date**: 2026-05-10
- **Deciders**: Architecture, Platform / SRE

## Context

The platform must run on AWS across three environments (dev / staging / prod) per ADR-0013, with three Aurora clusters per environment per ADR-0005, MSK Kafka, ElastiCache Redis, Glue Schema Registry (B2-2), S3 Object Lock (audit), Cognito, EKS, KMS, IRSA, External Secrets Operator. The only Terraform module that currently exists is `infra/cognito/terraform/` — a single flat module with the backend block commented out as `# placeholder`. Every other AWS resource enumerated in the architecture lives only in prose.

We cannot write the next 60+ Terraform PRs (VPC / EKS / Aurora x3 / MSK / Redis / Glue / S3 / IAM / KMS / Cognito) without first agreeing on:

1. **State layout** — per-resource-group `tfstate` (small blast radius) vs monolithic state (one apply for everything).
2. **Environment separation** — Terraform workspaces vs per-env directories vs per-env tfvars.
3. **Backend bootstrap** — the chicken-and-egg of "Terraform creates the bucket that holds Terraform's state."
4. **Multi-account vs single-account** — AWS Organizations layout.
5. **Module reuse boundary** — when do we wrap upstream modules (`terraform-aws-modules/*`) vs write our own?
6. **Naming and tagging** — discoverable resources and cost attribution.

This ADR closes those six points so that the IaC PR train can begin.

## Decision

### 1. State layout: per-stack remote state, one state per (stack × environment)

Each independently-deployable concern is a "stack." Each stack has its own `tfstate`, keyed as:

```
s3://inventory-platform-tfstate/aws/stacks/<stack>/<env>.tfstate
```

with a single DynamoDB table `inventory-platform-tflock` providing locking for all stacks.

**Stacks** (each = one `tfstate` × 3 envs = 3 state files):

| Stack | Owns | Depends on |
|---|---|---|
| `bootstrap` | tfstate bucket, lock table, KMS for state encryption | (none, local state then migrate) |
| `iam-baseline` | account-level IAM (OIDC provider, base IRSA roles, password policy) | bootstrap |
| `kms` | application CMKs (audit / aurora / s3 / secrets) | iam-baseline |
| `vpc` | VPC, subnets, NAT, route tables, VPC endpoints | bootstrap |
| `eks` | EKS cluster, node groups, Karpenter, OIDC association | vpc, iam-baseline, kms |
| `aurora` | 3 Aurora clusters (hot-path / business / common-base) per ADR-0005 | vpc, kms |
| `msk` | MSK cluster + topics (Glue SR integration) | vpc, kms, glue-schema-registry |
| `elasticache` | Redis (inventory-read-model + identity-broker revocation) | vpc, kms |
| `glue-schema-registry` | registry + Avro/JSON schemas per Kafka topic | iam-baseline, kms |
| `s3-audit` | audit bucket (Object Lock Compliance 365d) + CRR to ap-northeast-3 | iam-baseline, kms |
| `cognito` | User Pool + App Clients + SAML IdP (existing `infra/cognito/terraform/` migrates here) | iam-baseline |
| `eks-platform` | External Secrets Operator, Datadog DaemonSet, ArgoCD, Argo Rollouts, AWS Load Balancer Controller, Karpenter | eks |

State per env × per stack means 12 stacks × 3 envs = **36 tfstate files**. Blast radius of a bad apply is one stack × one env. Cross-stack references go through `terraform_remote_state` data sources.

### 2. Environment separation: per-env directory + per-env tfvars (no workspaces)

```
infra/aws/stacks/<stack>/
  main.tf              # composes modules, accepts var.environment
  variables.tf
  outputs.tf
  versions.tf
  backend.tf           # backend "s3" with key = "aws/stacks/<stack>/${var.environment}.tfstate"
  envs/
    dev.tfvars
    staging.tfvars
    prod.tfvars
```

Apply to dev: `terraform -chdir=infra/aws/stacks/vpc init -backend-config=envs/dev.backend.hcl` then `terraform -chdir=infra/aws/stacks/vpc apply -var-file=envs/dev.tfvars`. Each env-specific apply uses its own backend partial-config file because the S3 `key` itself varies per env.

**Workspaces explicitly rejected.** Workspaces share `versions.tf` / `variables.tf` but switch state via a hidden `terraform.tfstate.d/` directory; `var.environment` defaults are easy to forget; tooling sometimes auto-selects the wrong workspace. The directory pattern is idiomatic in the HashiCorp / Gruntwork ecosystem and survives PR diff review better.

### 3. Backend bootstrap: `bootstrap` stack runs first with local state, then migrates

`infra/aws/stacks/bootstrap/` creates the tfstate bucket + DynamoDB lock + KMS key for state encryption. Initial run uses local state (`backend.tf` declares no backend). After apply succeeds, `backend.tf` is uncommented to declare the S3 backend, and `terraform init -migrate-state` moves bootstrap's own state into the bucket it just created.

The bootstrap stack's `terraform.tfstate` from the local-state phase is committed to the repo (encrypted via `git-crypt` or stored only on the operator's disk — to be decided per environment). After migration, the local file is deleted. This is a **one-time per-environment bootstrap**.

Every other stack uses the S3 backend from day one.

### 4. Account topology: single AWS account with IAM-based env isolation (initially)

All three environments live in **one AWS account** under `inventory-platform`. Environment isolation is via:

- **Resource naming prefix**: `<env>-` on every resource name (e.g. `prod-vpc`, `staging-aurora-hotpath`).
- **IAM boundary policies**: dev/staging deploy roles cannot touch `prod-*` resources.
- **Separate KMS keys per env**: encryption boundary is env-scoped.
- **Separate VPCs per env**: network boundary.

**Multi-account migration is a future ADR.** AWS Organizations + Control Tower + per-env-account is the long-term target, but introducing it before any infrastructure exists adds enough operational load (SSO, AWS billing roll-up, cross-account assume-role) to slow down v1. The single-account phase is acceptable for first-customer launch (≤ small handful of tenants); the migration trigger is "compliance audit demands account-level isolation" or "second product line co-located in AWS." The naming and IAM boundary discipline above keeps the future migration mechanically straightforward.

### 5. Module strategy: thin wrappers around `terraform-aws-modules/*`, custom modules only when needed

The convention is:

- **Wrap upstream modules** (`terraform-aws-modules/vpc/aws`, `terraform-aws-modules/eks/aws`, `terraform-aws-modules/rds-aurora/aws`, etc.) inside `infra/aws/modules/<name>/` to bind project-specific defaults (tags, naming, KMS keys, encryption settings) and to insulate stacks from upstream version pin churn.
- **Custom modules** are written for things upstream does not cover well: `glue-schema-registry`, `s3-audit-bucket` (Object Lock + Compliance + CRR), `iam-irsa-role` (per-pod IAM with naming convention), `cognito-userpool` (the existing module migrates here).

Module versions are pinned at `versions.tf` per stack via `version = "~> X.Y"`. Renovate / Dependabot opens PRs for module bumps — not for major versions.

### 6. Naming and tagging

| Resource | Pattern | Example |
|---|---|---|
| AWS resources | `<env>-<purpose>` | `prod-vpc`, `prod-aurora-hotpath`, `prod-eks-cluster` |
| K8s namespaces | `<service>` (env baked in cluster) | `inventory-core`, `audit-service` |
| KMS keys | `alias/<env>-<purpose>` | `alias/prod-aurora`, `alias/prod-audit` |
| S3 buckets | `inventory-platform-<env>-<purpose>` | `inventory-platform-prod-audit`, `inventory-platform-prod-tfstate` |

Required tags on every taggable resource:
- `Environment` = `dev` / `staging` / `prod`
- `Project` = `inventory-platform`
- `ManagedBy` = `terraform`
- `Stack` = `<stack name>`
- `CostCenter` = (placeholder, finalized when finance assigns)

Tags are applied via `default_tags` in the AWS provider block + `module.shared_tags` for K8s-managed resources where the AWS provider does not reach.

### 7. Existing assets

- **`infra/cognito/terraform/`** keeps working in its current location for now (do not block the cognito follow-up tasks). It will be migrated into `infra/aws/stacks/cognito/` + `infra/aws/modules/cognito-userpool/` in a dedicated follow-up phase. The existing `terraform fmt -check -recursive infra/` CI gate continues to cover both layouts during the transition.
- **`infra/audit-s3/glue/*.sql`** stays in place — DDL is run via Athena CLI, not Terraform. The `s3-audit` stack creates the bucket and replaces `PLACEHOLDER_AUDIT_BUCKET` via a templated DDL output.
- **`infra/k8s/`** (raw manifests) stays for the `kubectl apply` MVP path. Argo CD takes over once `eks-platform` is stood up; manifests are then served from a `gitops/` directory referenced by Argo CD `Application` resources.
- **`infra/pact-broker/`** keeps Kustomize manifests; the `s3-audit`-style "infra-as-SQL" carve-out script for its Aurora-C database is moved into the `aurora` stack as a separate database resource.

### 8. CI integration

`.github/workflows/terraform.yml` is already in place with `fmt-check` and `validate-cognito` jobs. Extend it as new stacks land:

- One `validate-<stack>` job per stack, matrix over env tfvars.
- `terraform plan -detailed-exitcode` weekly drift detection job (read-only AWS credential, exit 0 / 2 / 1) — separate workflow `terraform-drift.yml` (deferred to follow-up).
- `tflint` and `checkov` static analysis jobs added once stacks accumulate (deferred).
- `terraform plan` posted to PR comment (deferred, requires Atlantis or equivalent).

## Consequences

**Positive.**

- **Small blast radius.** A bad apply takes one stack × one env. Plans are fast (each stack is ~50–500 resources, not thousands).
- **Parallel applies.** Independent stacks can be applied simultaneously (e.g. `vpc` + `iam-baseline` race-free). Long applies don't block short ones.
- **Clear ownership.** Each stack maps to a SRE / Platform sub-team's responsibility (network / data / compute / app platform).
- **Backwards compatible.** Existing `infra/cognito/terraform/` keeps working; migration is incremental and can be paused.
- **Discoverable.** A new engineer sees `infra/aws/stacks/` and immediately knows where each piece lives, sized at directory-listing-readable granularity.

**Negative.**

- **More files.** 12 stacks × 3 envs × ~5 files = ~180 small files at full layout. Mitigated by consistent skeleton — once one stack lands, the next is mostly copy-paste-rename.
- **Cross-stack data access via `terraform_remote_state`** is verbose vs same-state references. Mitigated by exporting only stable interface outputs and treating each stack like an API.
- **Bootstrap is a special case.** The local-state-first dance is documented in `infra/aws/stacks/bootstrap/README.md` and is a one-time operation per env. Operators must follow the runbook precisely.
- **Single-account isolation is weaker than per-env-account.** Acceptable for v1; the migration path is documented.

**Neutral.**

- Module wrapping adds an indirection layer, but production AWS infra always grows enough project-specific defaults that direct upstream-module usage in stacks becomes unreadable within a quarter. The wrappers pay for themselves quickly.

## Alternatives considered

### Option 1: Monolithic state (one tfstate for all infra)

A single `infra/aws/main.tf` for everything. **Rejected.** Plan time grows linearly with resource count and reaches >10 minutes within a quarter. Any apply touches everyone's resources. Bad apply blast radius is the entire account.

### Option 2: Terraform workspaces for env separation

`terraform workspace select prod` instead of per-env directories. **Rejected.** Workspace selection errors are silent and have caused real production incidents in other projects. Per-env directories make `git diff` show the env clearly. Workspaces also share `versions.tf` and `variables.tf`, which means env-specific provider version pinning becomes awkward.

### Option 3: Multi-account (AWS Organizations) from day one

Per-env AWS accounts via Organizations + Control Tower. **Rejected for v1**, accepted as future direction. Operational overhead (SSO, role-chaining, cross-account VPC peering, billing roll-up) is multi-week work that delays the first-customer launch. Single-account naming + IAM boundary discipline is sufficient pre-launch and the migration is mechanically straightforward later.

### Option 4: Terragrunt wrapper

Use Terragrunt to DRY up backend/provider configuration across stacks. **Rejected for now.** Adds a tool dependency that the team does not have experience with; the per-env-tfvars + per-stack-`backend.tf` pattern handles the same DRY concerns at the cost of a small amount of repetition. Re-evaluate when the stack count crosses ~20.

### Option 5: AWS CDK or Pulumi instead of Terraform

Imperative IaC. **Rejected.** Already implicitly decided in `architecture.md` (D1 — "IaC: Terraform"). HashiCorp + AWS provider has the largest community of AWS-specific examples for the resources we need (Aurora, MSK, Glue SR, EKS), and `terraform-aws-modules/*` is broadly maintained. Re-evaluation trigger is "team grows to >50 SREs and wants imperative composition."

## References

- ADR-0005 — Database ownership / 3 Aurora clusters per environment
- ADR-0013 — EKS topology (1 cluster per env, namespace per service)
- `memory/architecture.md` — D1 (CI/CD), D2 (DR), C4 (EKS topology)
- `infra/cognito/terraform/` — existing module layout, becomes the reference template after migration
- HashiCorp recommended state architecture: https://developer.hashicorp.com/terraform/cloud-docs/workspaces/best-practices
- `terraform-aws-modules/*` — upstream module collection used by wrapper modules
