output "karpenter_chart_version" {
  description = "deploy 中の Karpenter chart version。 trouble shoot / runbook で参照。"
  value       = var.karpenter_chart_version
}

output "default_nodepool_arch" {
  description = "default NodePool が起動する architecture (arm64 / amd64)。 services pod manifest の nodeSelector / nodeAffinity 設定参考。"
  value       = var.nodepool_default_arch
}

output "default_nodepool_cpu_limit" {
  description = "default NodePool の cluster 全体 CPU 上限 (vCPU)。"
  value       = var.nodepool_default_cpu_limit
}

output "default_nodepool_memory_limit" {
  description = "default NodePool の cluster 全体 memory 上限。"
  value       = var.nodepool_default_memory_limit
}

# ----------------------------------------------------------------------------
# External Secrets Operator
# ----------------------------------------------------------------------------

output "external_secrets_irsa_role_arn" {
  description = "ESO Controller IRSA role ARN。 監査 + service 側 ExternalSecret manifest の参考。"
  value       = module.external_secrets_irsa.iam_role_arn
}

output "external_secrets_chart_version" {
  description = "deploy 中の ESO chart version。"
  value       = var.external_secrets_chart_version
}

# ----------------------------------------------------------------------------
# AWS Load Balancer Controller
# ----------------------------------------------------------------------------

output "aws_lb_controller_irsa_role_arn" {
  description = "AWS Load Balancer Controller IRSA role ARN。 監査用。"
  value       = module.aws_lb_controller_irsa.iam_role_arn
}

output "aws_lb_controller_chart_version" {
  description = "deploy 中の AWS Load Balancer Controller chart version。"
  value       = var.aws_lb_controller_chart_version
}
