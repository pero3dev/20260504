output "controller_iam_role_arn" {
  description = <<-EOT
    Karpenter Controller IRSA role ARN。 Phase C で Helm chart を deploy する際、
    serviceAccount.annotations[eks.amazonaws.com/role-arn] に設定する。
  EOT
  value       = module.karpenter.controller_iam_role_arn
}

output "node_iam_role_arn" {
  description = "Karpenter NodePool node の IAM role ARN。 EC2NodeClass.spec.role で参照。"
  value       = module.karpenter.node_iam_role_arn
}

output "node_iam_role_name" {
  description = "Karpenter NodePool node の IAM role name。 EC2NodeClass.spec.role に渡す (name 形式)。"
  value       = module.karpenter.node_iam_role_name
}

output "instance_profile_name" {
  description = <<-EOT
    Karpenter NodePool node の Instance Profile 名。 v1 では EC2NodeClass.spec.role を渡せば
    Karpenter Controller 側で自動解決するので、 通常は直接参照不要。 互換性のため残す。
  EOT
  value       = module.karpenter.instance_profile_name
}

output "interruption_queue_name" {
  description = <<-EOT
    Spot 中断 + instance state change を受信する SQS queue 名。
    Karpenter Helm values の settings.interruptionQueue に渡す。
  EOT
  value       = module.karpenter.interruption_queue_name
}
