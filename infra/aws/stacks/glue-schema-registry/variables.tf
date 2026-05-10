variable "environment" {
  description = "dev / staging / prod。 registry 名 prefix と Environment tag に注入。"
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment は dev / staging / prod のいずれか。"
  }
}

variable "region" {
  description = "AWS リージョン。 Glue Schema Registry は region scoped (MSK と同 region)。"
  type        = string
  default     = "ap-northeast-1"
}

variable "registry_name_override" {
  description = "Registry 名上書き。 default は `<env>-platform-schemas`。"
  type        = string
  default     = ""
}
