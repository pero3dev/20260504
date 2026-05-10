output "cluster_id" {
  description = "EKS cluster ID = cluster_name (例: dev-platform)。"
  value       = module.eks.cluster_name
}

output "cluster_arn" {
  description = "EKS cluster ARN。"
  value       = module.eks.cluster_arn
}

output "cluster_endpoint" {
  description = "EKS API endpoint (private)。 kubectl の server URL。"
  value       = module.eks.cluster_endpoint
}

output "cluster_certificate_authority_data" {
  description = "kubectl の cluster CA data (base64)。"
  value       = module.eks.cluster_certificate_authority_data
}

output "cluster_oidc_issuer_url" {
  description = "OIDC provider issuer URL。 IRSA で role 作成時の trust policy に使う。"
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
    Node SG ID。 Phase B で Karpenter が application node を作る際、 本 SG を継承させる。
    aurora_client_sg / msk_client_sg / redis_client_sg を本 SG に追加 attach する設計
    (eks-platform stack で対応)。
  EOT
  value       = module.eks.node_security_group_id
}

output "ebs_csi_irsa_role_arn" {
  description = "EBS CSI driver の IRSA role ARN (kube-system:ebs-csi-controller-sa)。"
  value       = module.ebs_csi_irsa.iam_role_arn
}
