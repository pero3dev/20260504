# module: kms-key

1 用途 1 KMS key + alias を作る reusable module。 ADR-0024 の wrapper 方針に従い、 プロジェクト共通の defaults (rotation ON / 削除待機 30 日 / account root 全権) を bind する。

## 何をやっているか

- `aws_kms_key`: rotation ON、 削除待機 30 日 (最大、 安全側)、 key policy は account root のみ全権
- `aws_kms_alias`: 任意の `alias/<env>-<purpose>` 名を割り当て
- `additional_principals` で追加 IAM principal に kms:* を grant 可能 (v1 は空運用)

AWS service principal (rds.amazonaws.com 等) は本 module の policy には含めない。 downstream stack が KMS key を `kms_key_id` 引数で指定すると AWS が service-linked role 用の grant を自動生成するため、 key policy 側で明示する必要は無い。

## 入力

| 変数 | 説明 | デフォルト |
|---|---|---|
| `alias_name` | `alias/<env>-<purpose>` (alias/ prefix 必須) | (必須) |
| `description` | 用途と env が分かる文字列 | (必須) |
| `deletion_window_in_days` | 削除待機日数 (7〜30) | `30` |
| `enable_key_rotation` | annual auto-rotation | `true` |
| `additional_principals` | account root に加えて kms:* 可な IAM principal ARN | `[]` |
| `tags` | 追加 tag | `{}` |

## 出力

`key_id` / `key_arn` / `alias_name` / `alias_arn`

## 使用例

```hcl
module "aurora_key" {
  source = "../../modules/kms-key"

  alias_name  = "alias/dev-aurora"
  description = "Aurora encryption (dev environment)"
}

# downstream stack で:
# resource "aws_rds_cluster" "this" {
#   ...
#   kms_key_id = module.aurora_key.key_arn
# }
```

## 既知の制約

- key policy は account root のみ全権の最小構成。 service principal grants は AWS が自動生成するため明示不要だが、 cross-account 利用時は `additional_principals` で追加する必要あり (v1 単一 account 前提なので空)
- key policy を後で変更する PR は KMS API rate limit に注意 (毎秒 5 req まで)
