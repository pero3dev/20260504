output "cluster_id" {
  description = "EKS cluster 名 (例: dev-platform)。 kubectl context / Helm release name 等に使う。"
  value       = module.eks.cluster_id
}

output "cluster_arn" {
  description = "EKS cluster ARN。 IAM policy + monitoring 連携で参照。"
  value       = module.eks.cluster_arn
}

output "cluster_endpoint" {
  description = "EKS API endpoint (private)。 kubectl の server URL。"
  value       = module.eks.cluster_endpoint
}

output "cluster_certificate_authority_data" {
  description = "kubectl の cluster CA data (base64)。 kubeconfig 生成で使う。"
  value       = module.eks.cluster_certificate_authority_data
  sensitive   = true
}

output "cluster_oidc_issuer_url" {
  description = "OIDC provider issuer URL。 IRSA stack で role を作る際 trust policy に使う。"
  value       = module.eks.cluster_oidc_issuer_url
}

output "oidc_provider_arn" {
  description = "OIDC provider の ARN。 IRSA role の trust policy `Federated` field に使う。"
  value       = module.eks.oidc_provider_arn
}

output "cluster_security_group_id" {
  description = "EKS cluster 自身の SG ID。"
  value       = module.eks.cluster_security_group_id
}

output "node_security_group_id" {
  description = <<-EOT
    Node SG ID。 eks-platform stack で aurora_client / msk_client / redis_client を
    本 SG に attach することで、 全 node 上の pod が各 cluster に到達可能になる。
  EOT
  value       = module.eks.node_security_group_id
}

output "ebs_csi_irsa_role_arn" {
  description = "EBS CSI driver の IRSA role ARN (kube-system:ebs-csi-controller-sa)。"
  value       = module.eks.ebs_csi_irsa_role_arn
}
