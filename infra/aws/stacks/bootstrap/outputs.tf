output "tfstate_bucket" {
  description = "tfstate 保管 S3 バケット名。 各 stack の backend.tf の bucket 引数に渡す。"
  value       = aws_s3_bucket.tfstate.id
}

output "tfstate_bucket_arn" {
  description = "tfstate バケットの ARN。 IAM policy で deploy role に grant する際に参照する。"
  value       = aws_s3_bucket.tfstate.arn
}

output "tflock_table" {
  description = "DynamoDB lock table 名。 各 stack の backend.tf の dynamodb_table 引数に渡す。"
  value       = aws_dynamodb_table.tflock.name
}

output "tflock_table_arn" {
  description = "lock table の ARN。 IAM policy で deploy role に grant する際に参照する。"
  value       = aws_dynamodb_table.tflock.arn
}

output "tfstate_kms_key_arn" {
  description = "tfstate 暗号化 KMS key の ARN。 deploy role の kms:Decrypt 許可に使う。"
  value       = aws_kms_key.tfstate.arn
}

output "tfstate_kms_alias" {
  description = "tfstate 暗号化 KMS key の alias。"
  value       = aws_kms_alias.tfstate.name
}
