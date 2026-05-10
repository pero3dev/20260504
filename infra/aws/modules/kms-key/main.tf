# kms-key wrapper — 1 用途 1 KMS key + alias を作る reusable module。
#
# rotation ON / 削除待機 30 日 / account root 全権 がプロジェクト共通 default。
# additional_principals で追加の IAM principal に kms:* を grant 可能 (v1 は空)。
#
# AWS service principal (rds.amazonaws.com 等) は本 module の policy には含めない。
# downstream stack でリソース作成時に kms_key_id 指定すると、 AWS が service-linked role
# 用の grant を自動生成するため、 key policy 側での明示は不要。

data "aws_caller_identity" "current" {}

resource "aws_kms_key" "this" {
  description             = var.description
  deletion_window_in_days = var.deletion_window_in_days
  enable_key_rotation     = var.enable_key_rotation

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(
      [
        {
          Sid       = "AllowAccountRootFullAccess"
          Effect    = "Allow"
          Principal = { AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root" }
          Action    = "kms:*"
          Resource  = "*"
        }
      ],
      length(var.additional_principals) > 0 ? [
        {
          Sid       = "AllowAdditionalPrincipals"
          Effect    = "Allow"
          Principal = { AWS = var.additional_principals }
          Action    = "kms:*"
          Resource  = "*"
        }
      ] : [],
    )
  })

  tags = var.tags
}

resource "aws_kms_alias" "this" {
  name          = var.alias_name
  target_key_id = aws_kms_key.this.id
}
