# AWS Infra Terraform

ADR-0024 で確定した layout。 全 12 stacks + 共有 modules を 1 リポジトリで管理する。

## 構造

```
infra/aws/
  modules/                         # 再利用可能な building blocks (stacks から呼ばれる)
    vpc/
    eks-cluster/
    karpenter/                     # Karpenter Controller IRSA + Node IAM + SQS + EventBridge (Phase B-1)
    aurora-cluster/
    msk-cluster/                   # 未実装 (現在 stacks/msk が直書き、 module 化は post-v1)
    elasticache-redis/             # 未実装 (現在 stacks/elasticache が直書き、 module 化は post-v1)
    glue-schema-registry/          # 未実装 (現在 stacks/glue-schema-registry が直書き、 module 化は post-v1)
    s3-audit-bucket/
    iam-irsa-role/                 # 未実装 (上流 sub-module を直接呼ぶ運用)
    kms-key/
    cognito-userpool/              # 未実装 (infra/cognito/terraform/ 移行先、 別 follow-up)
  stacks/                          # 1 stack = 1 tfstate (env ごとに別 key)
    bootstrap/                     # tfstate バケット + lock table + KMS (env 非依存、 1 回限り)
    iam-baseline/                  # 全 env 共通 OIDC provider + base IRSA roles
    kms/                           # アプリ用 CMK 群 (env x 用途)
    vpc/                           # VPC + subnets + NAT + route tables (env)
    eks/                           # EKS cluster + system NG + OIDC + addons (env、 Phase A)
    eks-karpenter/                 # Karpenter Controller IRSA + Node IAM + SQS + subnet tags (env、 Phase B-1)
    aurora/                        # 3 Aurora clusters (hot-path / business / common-base) (env)
    msk/                           # MSK cluster + topics (env)
    elasticache/                   # Redis (env)
    glue-schema-registry/          # Glue SR + schemas per Kafka topic (env)
    s3-audit/                      # 監査バケット + Object Lock Compliance + CRR (env)
    cognito/                       # User Pool + App Clients + SAML IdP (env、 移行後)
    eks-platform/                  # Karpenter Helm + NodeClass/NodePool + ESO + ALB Controller + (後続) Datadog / ArgoCD / Argo Rollouts / per-service IRSA (env、 Phase C)
```

## State layout

backend は **単一 S3 バケット + 単一 DynamoDB lock table** を全 stack が共有する。 各 stack の state key は `aws/stacks/<stack>/<env>.tfstate` の規則で衝突しない。

| Stack | env | state key |
|---|---|---|
| `bootstrap` | (env 非依存) | `aws/stacks/bootstrap/main.tfstate` |
| `vpc` | dev | `aws/stacks/vpc/dev.tfstate` |
| `vpc` | staging | `aws/stacks/vpc/staging.tfstate` |
| `vpc` | prod | `aws/stacks/vpc/prod.tfstate` |
| ...同形を 11 stacks × 3 envs に展開 | | |

## Apply 手順 (一般)

env 依存 stack は `envs/<env>.backend.hcl` で per-env state key を partial init し、 `envs/<env>.tfvars` で per-env 値を渡す。

```bash
terraform -chdir=infra/aws/stacks/<stack> init \
  -backend-config=envs/<env>.backend.hcl
terraform -chdir=infra/aws/stacks/<stack> plan \
  -var-file=envs/<env>.tfvars
terraform -chdir=infra/aws/stacks/<stack> apply \
  -var-file=envs/<env>.tfvars
```

`bootstrap` のみ初回 local state で実行 → S3 backend に migrate という特殊手順。 詳細は `stacks/bootstrap/README.md`。

## 依存順序 (ADR-0024 で確定)

```
bootstrap
  ↓
iam-baseline + kms (並列)
  ↓
vpc
  ↓
eks  +  aurora  +  msk  +  elasticache  +  glue-schema-registry  +  s3-audit  +  cognito  (並列)
  ↓
eks-karpenter (Phase B-1: Karpenter AWS リソース)
  ↓
eks-platform (Phase C: Karpenter Helm + NodeClass/NodePool 完了、 ESO / Datadog / ArgoCD / Argo Rollouts / ALB / IRSA は後続 phase で本 stack に追加)
  ↓
K8s manifests × 13 services (terraform スコープ外、 GitOps repo へ)
```

## CI

`.github/workflows/terraform.yml` が:

- 全 `infra/**/*.tf` を `terraform fmt -check -recursive` で format gate
- 各 stack の `validate-<stack>` job を 1 個ずつ持つ (新 stack 追加時に job を 1 つ足す)

stack 数が増えたら matrix 化する (現状直書き)。

## 命名 + tagging

ADR-0024 で確定:

- リソース名: `<env>-<purpose>` (例: `prod-vpc`, `prod-aurora-hotpath`)
- KMS alias: `alias/<env>-<purpose>`
- S3 bucket: `inventory-platform-<env>-<purpose>`

必須 tags (provider `default_tags` で全 stack に注入):

- `Environment` = dev / staging / prod
- `Project` = inventory-platform
- `ManagedBy` = terraform
- `Stack` = stack 名
- `CostCenter` = (placeholder、 finance 確定時に上書き)

## 既存資産の扱い

- `infra/cognito/terraform/` — 当面そのまま動かし、 別 follow-up で `infra/aws/stacks/cognito/` + `infra/aws/modules/cognito-userpool/` に移行。 現行 follow-up を block しない
- `infra/audit-s3/glue/*.sql` — Athena CLI 経由で残置。 `s3-audit` stack が bucket name を template output で供給する経路に切替
- `infra/k8s/` — 生 manifest はそのまま。 `eks-platform` stack で ArgoCD が立った後、 GitOps repo へ移管 (manifests 自体は変えず Application 経由で sync)
- `infra/pact-broker/k8s/` — 同上
- `infra/tenant-provisioning/` — runbook 文書のみで terraform 化対象外
