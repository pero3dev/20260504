# s3-audit-bucket wrapper — ADR-0008 A4 の S3 Object Lock バケットを 1 セットで作る。
#
# 含むもの:
#   - aws_s3_bucket                                : object_lock_enabled = true で作成 (後付け不可)
#   - aws_s3_bucket_versioning                     : Object Lock の前提として Enabled
#   - aws_s3_bucket_object_lock_configuration      : Compliance mode + retention (default 365 日)
#   - aws_s3_bucket_server_side_encryption_*       : SSE-KMS (kms stack の audit-s3 key)
#   - aws_s3_bucket_public_access_block            : 全 4 設定 ON
#   - aws_s3_bucket_lifecycle_configuration        : 365 日後 expiration
#   - aws_s3_bucket_policy                         : Delete 系 + Lifecycle 変更系を全 principal Deny
#
# 含まないもの (downstream stack / IRSA 経路で対応):
#   - audit-service IRSA に対する Allow s3:PutObject  → eks-platform / audit-service IRSA stack
#   - 監査人 role に対する Allow s3:GetObject          → 監査人 role 作成時 IAM policy で
#
# 削除保護:
#   - aws_s3_bucket は prevent_destroy = true (Compliance mode のオブジェクトが存在すると
#     destroy 自体が AWS 側で拒否されるため、 二重 protection)。

resource "aws_s3_bucket" "this" {
  bucket              = var.bucket_name
  object_lock_enabled = true # 作成時のみ有効化可能、 後付け不可

  lifecycle {
    prevent_destroy = true
  }

  tags = var.tags
}

resource "aws_s3_bucket_versioning" "this" {
  bucket = aws_s3_bucket.this.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_object_lock_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    default_retention {
      mode = "COMPLIANCE"
      days = var.object_lock_retention_days
    }
  }

  depends_on = [aws_s3_bucket_versioning.this]
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = var.kms_key_arn
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "this" {
  bucket = aws_s3_bucket.this.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    id     = "expire-after-retention"
    status = "Enabled"

    filter {}

    expiration {
      days = var.lifecycle_expiration_days
    }

    noncurrent_version_expiration {
      noncurrent_days = var.lifecycle_expiration_days
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

# Bucket policy: Delete + Lifecycle/Versioning/ObjectLock 設定変更を全 principal で Deny。
# Allow 系 (audit-service の PUT / 監査人 role の GET) は IRSA / 監査人 role 側 IAM policy で attach。
data "aws_iam_policy_document" "this" {
  statement {
    sid    = "DenyDeleteObject"
    effect = "Deny"
    principals {
      type        = "AWS"
      identifiers = ["*"]
    }
    actions = [
      "s3:DeleteObject",
      "s3:DeleteObjectVersion",
    ]
    resources = ["${aws_s3_bucket.this.arn}/*"]
  }

  statement {
    sid    = "DenyBucketLifecycleChange"
    effect = "Deny"
    principals {
      type        = "AWS"
      identifiers = ["*"]
    }
    actions = [
      "s3:PutLifecycleConfiguration",
      "s3:PutBucketObjectLockConfiguration",
      "s3:PutBucketVersioning",
    ]
    resources = [aws_s3_bucket.this.arn]
  }
}

resource "aws_s3_bucket_policy" "this" {
  bucket = aws_s3_bucket.this.id
  policy = data.aws_iam_policy_document.this.json
}
