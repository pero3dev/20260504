# bootstrap stack — Terraform state を保管するインフラを Terraform 自身で作る。
#
# chicken-and-egg 構造のため、 初回 apply は local state で実行 → migrate して
# 自分自身の state を S3 バケットに移す。 詳細は本 stack の README.md。
#
# 対象リソース:
#   - aws_kms_key + aws_kms_alias    : state 暗号化用 CMK (alias/tfstate)
#   - aws_s3_bucket                   : state 本体保管 (versioning + KMS + public block + lifecycle)
#   - aws_dynamodb_table              : state lock (全 stack 共通の 1 table)
#
# 影響範囲: 全 stack の plan/apply が本バケット + lock table を読む。 削除は plan 全停止と等価。
# 命名規則は ADR-0024 の `<env>-<purpose>` ではなく、 env 非依存の共有インフラとして
# `inventory-platform-<purpose>` を採用 (env tag も付与しない)。

# ----------------------------------------------------------------------------
# KMS key for state encryption
# ----------------------------------------------------------------------------

resource "aws_kms_key" "tfstate" {
  description             = "Encryption key for Terraform state (inventory-platform shared bucket)."
  deletion_window_in_days = 30
  enable_key_rotation     = true

  # state 復号は terraform を実行する operator role / CI role に限定する。
  # 本 stack 時点では IAM stack 未着手のため、 root + 自 account principal の admin 権限のみ。
  # iam-baseline stack 完成後に key policy を狭める PR を別途出す。
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowAccountRootFullAccess"
        Effect    = "Allow"
        Principal = { AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root" }
        Action    = "kms:*"
        Resource  = "*"
      }
    ]
  })
}

resource "aws_kms_alias" "tfstate" {
  name          = var.tfstate_kms_alias
  target_key_id = aws_kms_key.tfstate.id
}

data "aws_caller_identity" "current" {}

# ----------------------------------------------------------------------------
# S3 bucket for tfstate
# ----------------------------------------------------------------------------

resource "aws_s3_bucket" "tfstate" {
  bucket = var.tfstate_bucket_name

  # state は誤削除すると全 stack が plan できなくなるので、 destroy ガード必須。
  # apply 直後の意図的破棄が必要な場合は本属性を一時的に false にする運用。
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.tfstate.arn
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    id     = "expire-old-state-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = var.tfstate_noncurrent_version_retention_days
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

# ----------------------------------------------------------------------------
# DynamoDB lock table
# ----------------------------------------------------------------------------

resource "aws_dynamodb_table" "tflock" {
  name         = var.tflock_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  server_side_encryption {
    enabled     = true
    kms_key_arn = aws_kms_key.tfstate.arn
  }

  point_in_time_recovery {
    enabled = true
  }

  # 削除は全 stack の lock 取得不能を引き起こす。 destroy ガード必須。
  lifecycle {
    prevent_destroy = true
  }
}
