# kms stack

ADR-0024 で 4 番目に走らせる stack。 env ごとに 4 種のアプリ用 CMK を作る。

## 何を作るか

env あたり 4 つの KMS key + alias:

| Alias | 用途 |
|---|---|
| `alias/<env>-aurora` | Aurora 3 cluster (hot-path / business / common-base) at-rest 暗号化 |
| `alias/<env>-audit-s3` | audit-service の S3 Object Lock バケット (compliance mode 365 days) |
| `alias/<env>-secrets` | Secrets Manager で管理する全 secret (JWT 鍵 / Aurora master / SES SMTP 等) |
| `alias/<env>-ebs` | EKS node groups の EBS volume + PVC |

各 key は `modules/kms-key` 経由で生成 (rotation ON / 削除待機 30 日 / account root 全権 policy)。 `Project / ManagedBy / Stack=kms / Environment=<env>` tag を `default_tags` で全 key に注入。

## 範囲外 (本 stack で作らないもの)

- **MSK 専用 CMK**: v1 では AWS-managed key で運用、 SecOps 要件確定後に別 CMK 化検討
- **ElastiCache 専用 CMK**: v1 では AWS-managed key、 同上
- **tfstate 用 CMK**: bootstrap stack (`alias/tfstate`) で作成済、 env 非依存
- **Cognito 用 CMK**: Cognito User Pool は AWS-managed key で十分 (token 自体は別経路で署名)

将来 cmk が必要になった用途は本 stack に module ブロックを 1 個足すだけで追加可能。

## Apply 手順

bootstrap + iam-baseline + vpc 完了後 (vpc は厳密には KMS と独立だが、 並列フェーズ規定上 KMS は独立して走らせ可能)。

```bash
cd infra/aws/stacks/kms

# dev
terraform init -backend-config=envs/dev.backend.hcl
terraform plan -var-file=envs/dev.tfvars
terraform apply -var-file=envs/dev.tfvars

# staging
terraform init -reconfigure -backend-config=envs/staging.backend.hcl
terraform plan -var-file=envs/staging.tfvars
terraform apply -var-file=envs/staging.tfvars

# prod
terraform init -reconfigure -backend-config=envs/prod.backend.hcl
terraform plan -var-file=envs/prod.tfvars
terraform apply -var-file=envs/prod.tfvars
```

完了後、 outputs から各 key ARN / alias を控える。 後続 stack (aurora / s3-audit / secrets / eks) が `terraform_remote_state` で本 stack の outputs を参照する。

## 命名規則

ADR-0024 で確定:

- alias: `alias/<env>-<purpose>` (例: `alias/prod-aurora`)
- 用途名は kebab-case (例: `audit-s3` で `_` ではなく `-`)

将来 multi-region 化時は `alias/<env>-<region>-<purpose>` に拡張する想定 (本 stack は単一 region 前提なので region は alias に含めない)。

## key policy

各 key の policy は account root のみに kms:* を allow する最小構成。 service principal grants は AWS が downstream stack の resource 作成時に自動生成するため明示不要。

cross-account 利用や追加 IAM principal への grant が必要な場合は、 `modules/kms-key` の `additional_principals` 引数経由で追加。 v1 単一 account 前提のため空運用。

## 削除

KMS key は削除待機 30 日。 `terraform destroy` 後 30 日以内なら AWS console / CLI で `kms:CancelKeyDeletion` 実行で復旧可能。 30 日経過すると復旧不能で、 暗号化済みデータも復号不能になる (Aurora snapshot 等が完全に失われる)。

本番 key 削除は SRE / Platform レビュー必須。 通常運用では `prevent_destroy` を付けないが、 用途が確定したら個別 key に追加する PR を出す方針。

## 既知の制約

- v1 では key policy が account root のみ。 deploy role が AdminAccess を持っているので IAM 経由で kms:* 可能だが、 IAM の最小権限化を進めると本 policy 側に明示の許可追加が必要になる
- key rotation は annual (AWS の自動 rotation で 365 日に 1 回新 backing key)。 旧 backing key は復号用に保持される (delete されない) のでデータアクセスは継続可能
- KMS API rate limit は region 全体で秒間数千 req まで。 大量 secret retrieval (起動時の全 pod 初期化等) で頻度を超える場合は KMS DataKey caching 等の検討が必要
