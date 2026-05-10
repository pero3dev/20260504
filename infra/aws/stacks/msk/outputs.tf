output "cluster_arn" {
  description = "MSK Serverless cluster ARN。 IAM policy + monitoring 連携で参照する。"
  value       = aws_msk_serverless_cluster.this.arn
}

output "cluster_name" {
  description = "MSK cluster 名 (例: dev-platform-msk)。"
  value       = aws_msk_serverless_cluster.this.cluster_name
}

output "bootstrap_brokers_sasl_iam" {
  description = <<-EOT
    SASL/IAM 認証用の bootstrap brokers URL。 Spring Kafka の `spring.kafka.bootstrap-servers` に渡す。
    serverless の brokers URL は data source で取得する必要あり (cluster resource は直接 expose しない)。
  EOT
  value       = data.aws_msk_bootstrap_brokers.this.bootstrap_brokers_sasl_iam
}

output "msk_client_security_group_id" {
  description = <<-EOT
    MSK に接続する側 (EKS node group / pod) が attach する SG ID。
    eks-platform stack で node group の additional_security_group_ids に追加する。
  EOT
  value       = aws_security_group.msk_client.id
}

output "msk_cluster_security_group_id" {
  description = "MSK cluster 自身の SG。 監査等で参照可能。"
  value       = aws_security_group.msk_cluster.id
}

output "msk_client_iam_policy_json" {
  description = <<-EOT
    services の IRSA role に attach する用 IAM policy JSON (Connect + Topic 操作)。
    実 attach は per-service IRSA stack で aws_iam_policy + aws_iam_role_policy_attachment を行う。
  EOT
  value       = data.aws_iam_policy_document.msk_client.json
}

# bootstrap brokers は cluster resource に直接含まれないので data source で取得する。
data "aws_msk_bootstrap_brokers" "this" {
  cluster_arn = aws_msk_serverless_cluster.this.arn
}
