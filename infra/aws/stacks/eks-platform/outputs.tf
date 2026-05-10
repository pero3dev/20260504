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
