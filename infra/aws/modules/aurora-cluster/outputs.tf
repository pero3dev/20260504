output "cluster_id" {
  description = "Aurora cluster ID。"
  value       = module.aurora.cluster_id
}

output "cluster_arn" {
  description = "Aurora cluster ARN。 IAM policy / Performance Insights URL 構築に使う。"
  value       = module.aurora.cluster_arn
}

output "cluster_endpoint" {
  description = "writer endpoint。 services の primary 接続 (write + read)。"
  value       = module.aurora.cluster_endpoint
}

output "cluster_reader_endpoint" {
  description = "reader endpoint (load balanced across readers)。 read 専用 worker pool で分けたい場合に使う。"
  value       = module.aurora.cluster_reader_endpoint
}

output "cluster_database_name" {
  description = "初期 database 名 (例: platform)。 services は per-service DB を Flyway Job で別途作成する。"
  value       = module.aurora.cluster_database_name
}

output "master_user_secret_arn" {
  description = <<-EOT
    AWS RDS managed master password の Secrets Manager secret ARN。
    admin 操作 (DB 作成 / user 作成 / GRANT) は本 secret を使う、 通常運用は per-service user を別途作成。
  EOT
  value       = try(module.aurora.cluster_master_user_secret[0].secret_arn, null)
}

output "cluster_security_group_id" {
  description = "Aurora cluster 自身の SG ID。 ingress は client_security_group_id からのみ許可。"
  value       = module.aurora.security_group_id
}
