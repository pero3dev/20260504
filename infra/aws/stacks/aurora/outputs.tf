# ============================================================================
# 共有 client SG
# ============================================================================

output "aurora_client_security_group_id" {
  description = <<-EOT
    Aurora に接続する側 (EKS node group) が attach する SG ID。
    本 SG が付いた node 上の pod は全 3 cluster に到達可能 (cluster 越え制御は credential 側で行う前提)。
    eks-platform stack で node group の additional_security_group_ids に追加する。
  EOT
  value       = aws_security_group.aurora_client.id
}

# ============================================================================
# hot-path cluster
# ============================================================================

output "hotpath_cluster_id" {
  description = "hot-path cluster ID (例: dev-aurora-hotpath)。"
  value       = module.hotpath.cluster_id
}

output "hotpath_cluster_endpoint" {
  description = "hot-path writer endpoint。 inventory-core / master-data の primary 接続。"
  value       = module.hotpath.cluster_endpoint
}

output "hotpath_cluster_reader_endpoint" {
  description = "hot-path reader endpoint。 read-heavy worker pool 用。"
  value       = module.hotpath.cluster_reader_endpoint
}

output "hotpath_master_user_secret_arn" {
  description = "hot-path master password の Secrets Manager secret ARN (admin 操作用)。"
  value       = module.hotpath.master_user_secret_arn
}

# ============================================================================
# business cluster
# ============================================================================

output "business_cluster_id" {
  description = "business cluster ID (例: dev-aurora-business)。"
  value       = module.business.cluster_id
}

output "business_cluster_endpoint" {
  description = "business writer endpoint。 retail-ec / wholesale / tpl / manufacturing の primary 接続。"
  value       = module.business.cluster_endpoint
}

output "business_cluster_reader_endpoint" {
  description = "business reader endpoint。"
  value       = module.business.cluster_reader_endpoint
}

output "business_master_user_secret_arn" {
  description = "business master password の Secrets Manager secret ARN。"
  value       = module.business.master_user_secret_arn
}

# ============================================================================
# common-base cluster
# ============================================================================

output "common_cluster_id" {
  description = "common-base cluster ID (例: dev-aurora-common)。"
  value       = module.common.cluster_id
}

output "common_cluster_endpoint" {
  description = "common-base writer endpoint。 identity-broker / audit / notification / workflow / analytics / integration-hub の primary 接続。"
  value       = module.common.cluster_endpoint
}

output "common_cluster_reader_endpoint" {
  description = "common-base reader endpoint。"
  value       = module.common.cluster_reader_endpoint
}

output "common_master_user_secret_arn" {
  description = "common-base master password の Secrets Manager secret ARN。"
  value       = module.common.master_user_secret_arn
}
