output "key_id" {
  description = "KMS key の UUID。 多くの AWS リソース API は ARN ではなく id を要求する。"
  value       = aws_kms_key.this.id
}

output "key_arn" {
  description = "KMS key の ARN。 IAM policy の Resource フィールド等に使う。"
  value       = aws_kms_key.this.arn
}

output "alias_name" {
  description = "KMS alias 名 (例: alias/dev-aurora)。 リソース指定で id/arn の代わりに使える。"
  value       = aws_kms_alias.this.name
}

output "alias_arn" {
  description = "KMS alias の ARN。 cross-account grant 等で参照する。"
  value       = aws_kms_alias.this.arn
}
