output "registry_name" {
  description = "Glue Schema Registry 名 (例: dev-platform-schemas)。 Confluent / AWS Glue Schema Registry serializer の `registry.name` 設定に渡す。"
  value       = aws_glue_registry.this.registry_name
}

output "registry_arn" {
  description = "Glue Schema Registry の ARN。 IAM policy + msk topic 連携設定で参照する。"
  value       = aws_glue_registry.this.arn
}
