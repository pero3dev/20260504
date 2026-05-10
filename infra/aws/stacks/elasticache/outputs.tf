output "primary_endpoint_address" {
  description = <<-EOT
    Redis primary node の DNS endpoint。 Spring Boot の spring.redis.host 等に渡す。
    cluster mode disabled なので primary に書き、 read replica からも (writer 経由で) 読める。
  EOT
  value       = aws_elasticache_replication_group.this.primary_endpoint_address
}

output "reader_endpoint_address" {
  description = <<-EOT
    Reader endpoint。 read replica の負荷分散用。 read 専用 worker pool が分かれている場合
    に使う (本 platform は v1 で primary 経由統一なので ref のみ)。
  EOT
  value       = aws_elasticache_replication_group.this.reader_endpoint_address
}

output "port" {
  description = "Redis port (= 6379)。"
  value       = aws_elasticache_replication_group.this.port
}

output "auth_token_secret_arn" {
  description = <<-EOT
    AUTH token を保管する Secrets Manager secret の ARN。 services の Pod が
    External Secrets Operator 経由で参照し、 spring.redis.password にマッピングする。
  EOT
  value       = aws_secretsmanager_secret.redis_auth.arn
}

output "auth_token_secret_name" {
  description = "Secrets Manager secret 名 (例: dev/platform/redis/auth-token)。 ESO ExternalSecret 定義で参照する。"
  value       = aws_secretsmanager_secret.redis_auth.name
}

output "redis_client_security_group_id" {
  description = <<-EOT
    Redis に接続する側 (EKS node group / Pod) が attach する SG の ID。
    本 SG が付いていないと redis_cluster SG の ingress を通れない。
    eks-platform stack の node group SG / IRSA-bound SG に追加 attach する。
  EOT
  value       = aws_security_group.redis_client.id
}

output "redis_cluster_security_group_id" {
  description = "Redis cluster 自身の SG。 通常 services から直接参照する必要は無いが、 監査等で参照可能。"
  value       = aws_security_group.redis_cluster.id
}
