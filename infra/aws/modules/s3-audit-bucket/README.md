# module: s3-audit-bucket

ADR-0008 A4 の S3 Object Lock バケット (Compliance mode + KMS + lifecycle + Deny policy) を 1 セットで作る reusable module。 既存 `infra/audit-s3/` の手動 runbook (Step 1〜5) を IaC 化した実体。

## 何をやっているか

- `aws_s3_bucket` (object_lock_enabled = true、 `prevent_destroy`)
- `aws_s3_bucket_versioning` (Enabled、 Object Lock 前提)
- `aws_s3_bucket_object_lock_configuration` (Compliance mode + 365 日 retention default)
- `aws_s3_bucket_server_side_encryption_configuration` (SSE-KMS、 kms stack の CMK)
- `aws_s3_bucket_public_access_block` (4 設定全 ON)
- `aws_s3_bucket_lifecycle_configuration` (365 日後 expire、 retention 期限と整合)
- `aws_s3_bucket_policy` (Delete 系 + Lifecycle 変更系 を Principal `*` で Deny)

## 範囲外 (本 module で扱わないもの)

- audit-service の S3 PUT 許可 → audit-service IRSA role の IAM policy で扱う
- 監査人 role の S3 GET 許可 → 監査人 role 作成時の IAM policy で扱う
- CRR (Cross-Region Replication) → 別 module / 別 PR で東京 → 大阪レプリカ追加

これらは「リソース所有者 (本 module) ではなく利用者側 IAM」 に紐付けるのが S3 / IAM の慣例。

## 入力

| 変数 | 説明 | デフォルト |
|---|---|---|
| `bucket_name` | バケット名 (例: `inventory-platform-prod-audit`) | (必須) |
| `kms_key_arn` | SSE-KMS 用 CMK ARN | (必須) |
| `object_lock_retention_days` | Compliance mode retention 日数 | `365` |
| `lifecycle_expiration_days` | object 自動削除日数 | `365` |
| `tags` | 追加 tag | `{}` |

## 出力

`bucket_id` / `bucket_arn` / `bucket_regional_domain_name`

## Compliance mode の制約

- 一度有効化すると、 retention 期間中は **AWS root user でも削除/上書き不可**
- retention 期間を **短くする変更は不可** (長くするのは可)
- バケット自体の destroy は protected object が存在中は AWS 側で拒否される (`terraform destroy` 失敗)

廃棄したい場合は:
1. retention 期限経過を待つ (365 日)
2. 全 object を delete (lifecycle 任せか手動)
3. `prevent_destroy` を一時的に false にする PR
4. `terraform destroy`

通常運用ではこの手順を踏まない。

## 既知の制約

- bucket policy の Allow statement は audit-service IRSA / 監査人 role 作成後に IAM 側で追加する設計。 IRSA role が無い段階で本 module を apply すると、 audit-service が S3 PUT で `Access Denied` になるが、 これは IRSA 接続後に解消される
- S3 access logging は v1 では無効化 (cost 観点)。 SecOps 要件確定後に enable する別 PR を出す
