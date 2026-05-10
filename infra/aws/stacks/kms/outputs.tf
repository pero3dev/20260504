output "aurora_key_arn" {
  description = "Aurora 用 CMK ARN。 aurora stack で kms_key_id 引数に渡す。"
  value       = module.aurora_key.key_arn
}

output "aurora_key_alias" {
  description = "Aurora 用 CMK alias (例: alias/dev-aurora)。"
  value       = module.aurora_key.alias_name
}

output "audit_s3_key_arn" {
  description = "Audit S3 用 CMK ARN。 s3-audit stack で bucket SSE-KMS に渡す。"
  value       = module.audit_s3_key.key_arn
}

output "audit_s3_key_alias" {
  description = "Audit S3 用 CMK alias (例: alias/dev-audit-s3)。"
  value       = module.audit_s3_key.alias_name
}

output "secrets_key_arn" {
  description = "Secrets Manager 用 CMK ARN。 全 secret resource の kms_key_id に渡す。"
  value       = module.secrets_key.key_arn
}

output "secrets_key_alias" {
  description = "Secrets Manager 用 CMK alias (例: alias/dev-secrets)。"
  value       = module.secrets_key.alias_name
}

output "ebs_key_arn" {
  description = "EBS / EKS PVC 用 CMK ARN。 EKS node group + StorageClass の encryption 設定に渡す。"
  value       = module.ebs_key.key_arn
}

output "ebs_key_alias" {
  description = "EBS / EKS PVC 用 CMK alias (例: alias/dev-ebs)。"
  value       = module.ebs_key.alias_name
}
