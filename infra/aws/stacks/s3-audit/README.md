# s3-audit stack

ADR-0024 並列フェーズの 1 つ。 ADR-0008 A4 で定義した audit-service S3 Object Lock バケット + Athena Glue Catalog (database + 2 tables) を env ごとに 1 セット作る。

## 何を作るか

env あたり:

- **S3 バケット** `inventory-platform-<env>-audit` (Object Lock Compliance mode 365 日 + SSE-KMS + Versioning + lifecycle 365 日 + public block + Deny policy)
- **Glue Catalog database** `audit_db_<env>`
- **Glue Catalog table** `audit_records` (JSONL gzip + partition projection `tenant × date`)
- **Glue Catalog table** `audit_anchors` (JSON 単発 + 同 partition projection)

partition projection 有効なので `ALTER TABLE ADD PARTITION` 不要。 audit-service が `s3://<bucket>/audit-records/tenant=<id>/date=<yyyy-MM-dd>/records.jsonl.gz` の規則で PUT すれば Athena が自動で見つける。

## 依存

- `kms` stack の `audit_s3_key_arn` output (= `alias/<env>-audit-s3` の ARN) を `terraform_remote_state` 経由で読み込み

## Apply 手順

bootstrap + iam-baseline + kms 完了後:

```bash
cd infra/aws/stacks/s3-audit

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

完了後 outputs から bucket name / Glue database 名を控える:

- `bucket_id` → audit-service env var `PLATFORM_AUDIT_ARCHIVE_BUCKET`
- `glue_database_name` → 監査人が Athena で `SELECT ... FROM audit_db_<env>.audit_records` の形で query

## audit-service のセットアップ (本 stack 完了後の手順)

audit-service が S3 PUT できるよう IRSA role + IAM policy を別 stack / 別 PR で作る:

```yaml
# audit-service Pod の K8s ServiceAccount
metadata:
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::<account>:role/audit-service-irsa-<env>
```

IRSA role の inline policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::inventory-platform-<env>-audit/*"
    },
    {
      "Effect": "Allow",
      "Action": ["kms:Encrypt", "kms:GenerateDataKey"],
      "Resource": "<output: kms.audit_s3_key_arn>"
    }
  ]
}
```

audit-service の env var:

```yaml
- name: PLATFORM_AUDIT_ARCHIVE_ENABLED
  value: "true"
- name: PLATFORM_AUDIT_ARCHIVE_BUCKET
  value: "inventory-platform-<env>-audit"
- name: PLATFORM_AUDIT_ARCHIVE_REGION
  value: "ap-northeast-1"
# endpoint-override は本番では空 (LocalStack 時のみ http://localstack:4566 等)
```

## 監査人 role のセットアップ (compliance audit 時)

監査人が Athena からクエリできるよう、 別途監査人 role を作る:

```json
{
  "Effect": "Allow",
  "Action": ["s3:GetObject", "s3:ListBucket"],
  "Resource": [
    "arn:aws:s3:::inventory-platform-<env>-audit",
    "arn:aws:s3:::inventory-platform-<env>-audit/*"
  ]
}
```

```json
{
  "Effect": "Allow",
  "Action": [
    "glue:GetDatabase",
    "glue:GetTable",
    "glue:GetPartitions",
    "athena:StartQueryExecution",
    "athena:GetQueryExecution",
    "athena:GetQueryResults"
  ],
  "Resource": "*"
}
```

KMS の `kms:Decrypt` も同 role に必要 (Glue/Athena が SSE-KMS object 読み込み時)。

## Bucket policy

本 stack の `modules/s3-audit-bucket` が **Deny** statement のみ含む policy を attach する:

- `DenyDeleteObject` (全 principal): `s3:DeleteObject` / `s3:DeleteObjectVersion`
- `DenyBucketLifecycleChange` (全 principal): `s3:PutLifecycleConfiguration` / `s3:PutBucketObjectLockConfiguration` / `s3:PutBucketVersioning`

Allow statement (audit-service PUT / 監査人 GET) は IRSA / 監査人 role 側 IAM policy で付与する設計。 「リソース所有者 (バケット) は Deny を集中、 Allow は利用者側に分散」 が最小原則。

## Compliance mode の制約 (再掲)

- 一度有効化したら、 retention 期限内 (365 日) は **AWS root user でも削除不可**
- retention 期間を **短くする変更は不可** (長くするのは可)
- バケット destroy は protected object 存在中に AWS 側で拒否される (`terraform destroy` 失敗)

廃棄時の手順は `modules/s3-audit-bucket/README.md` 参照。 通常運用では bucket を削除しない。

## Glue tables の schema

既存 `infra/audit-s3/glue/*.sql` (Athena CREATE EXTERNAL TABLE 形式) の terraform 化。 schema は完全一致:

| audit_records 列 | 型 |
|---|---|
| tenantid / action / targettype / targetid / operatoruserid / operatortenantid / outcome / errorcode / payloadjson / occurredat / prevhash / hash | string |
| sequence / eventid | bigint |
| readonly | boolean |
| (partition: tenant / date) | string |

| audit_anchors 列 | 型 |
|---|---|
| tenantid / anchordate / roothash / computedat | string |
| recordcount / firstsequence / lastsequence | bigint |
| (partition: tenant / date) | string |

**注**: Glue Catalog の columns は AWS 内部で lowercase 化される。 audit-service が PUT する JSON の field 名は camelCase (`tenantId` 等) だが、 JsonSerDe の `case.insensitive=true` で吸収される (Athena query 時は lowercase で書く)。

## 既存 `infra/audit-s3/` との関係

`infra/audit-s3/` の手動 runbook (Step 1〜10) を本 stack で IaC 化した。 元 SQL ファイル (`glue/audit_records_table.sql` / `audit_anchors_table.sql`) と JSON (`bucket-config/*.json`) は IaC が裏で何をするかの reference として残置。 `infra/audit-s3/README.md` 先頭に deprecation notice を追記済。

## 既知の制約

- CRR (東京 → 大阪 ap-northeast-3) は本 stack 範囲外。 別 PR で `aws_s3_bucket_replication_configuration` 追加予定 (DR 要件)
- VPC endpoint for S3 は vpc stack で gateway endpoint として既に有効化済 (private + database route table)。 audit-service が EKS 上で動く前提なら NAT 経由せず S3 にアクセスできる
- prod の Compliance mode + 365 日 retention は事故由来削除を完全に防ぐ反面、 「テスト中の試験 PUT」 もすべて 365 日残存する。 dev/staging で同じ retention を使うのは過剰。 必要なら dev 用に retention を短くする (ただし Compliance では事後変更不可)、 または GOVERNANCE mode に切替える PR を別途検討
