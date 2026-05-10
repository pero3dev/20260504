output "controller_iam_role_arn" {
  description = "Karpenter Controller IRSA role ARN。 Helm values の serviceAccount.annotations[eks.amazonaws.com/role-arn] に渡す。"
  value       = module.karpenter.iam_role_arn
}

output "controller_iam_role_name" {
  description = "Karpenter Controller IRSA role name。 監視 / 監査の参照用。"
  value       = module.karpenter.iam_role_name
}

output "node_iam_role_arn" {
  description = "Karpenter NodePool node 用の IAM role ARN。 EC2NodeClass の role field に渡す。"
  value       = module.karpenter.node_iam_role_arn
}

output "node_iam_role_name" {
  description = "Karpenter NodePool node 用の IAM role name。 EC2NodeClass の role field (name) に渡す。"
  value       = module.karpenter.node_iam_role_name
}

output "instance_profile_name" {
  description = <<-EOT
    Karpenter NodePool node の Instance Profile 名。 Karpenter v1.0 以降は EC2NodeClass の
    role field を渡せば controller 側で自動的に instance profile を解決するため、 通常は
    直接参照不要。 互換性のため残す。
  EOT
  value       = module.karpenter.instance_profile_name
}

output "interruption_queue_name" {
  description = "Spot 中断通知を受信する SQS queue 名。 Helm values の settings.interruptionQueue に渡す。"
  value       = module.karpenter.queue_name
}

output "interruption_queue_arn" {
  description = "SQS queue ARN。 監視 / IAM 監査の参照用。"
  value       = module.karpenter.queue_arn
}
