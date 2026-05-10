variable "environment" {
  description = "dev / staging / prod。 alias prefix `<env>-` と Environment tag に注入。"
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment は dev / staging / prod のいずれか。"
  }
}

variable "region" {
  description = "AWS リージョン。 KMS は region scoped なので env の primary region と一致させる。"
  type        = string
  default     = "ap-northeast-1"
}
