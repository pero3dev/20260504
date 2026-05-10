output "bucket_id" {
  description = "S3 バケット名 (= bucket_name)。 audit-service env var PLATFORM_AUDIT_ARCHIVE_BUCKET に渡す。"
  value       = aws_s3_bucket.this.id
}

output "bucket_arn" {
  description = "S3 バケットの ARN。 IAM policy resource フィールド + Glue table location 算出に使う。"
  value       = aws_s3_bucket.this.arn
}

output "bucket_regional_domain_name" {
  description = "regional endpoint (例: bucket.s3.ap-northeast-1.amazonaws.com)。 SDK 初期化で使うことがある。"
  value       = aws_s3_bucket.this.bucket_regional_domain_name
}
