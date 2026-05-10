variable "environment" {
  description = "dev / staging / prod。"
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment は dev / staging / prod のいずれか。"
  }
}

variable "region" {
  description = "AWS リージョン。"
  type        = string
  default     = "ap-northeast-1"
}

variable "engine_version" {
  description = "Aurora PostgreSQL engine version。 全 cluster で統一。"
  type        = string
  default     = "16.4"
}

# ----------------------------------------------------------------------------
# hot-path cluster (inventory-core + master-data)
# ----------------------------------------------------------------------------

variable "hotpath_instance_class" {
  description = "hot-path cluster の instance class。 11.6k TPS 想定で prod は memory-optimized。"
  type        = string
}

variable "hotpath_instance_count" {
  description = "hot-path cluster の instance 数 (writer + readers)。 prod は 3 推奨。"
  type        = number
}

# ----------------------------------------------------------------------------
# business cluster (retail-ec / wholesale / tpl / manufacturing)
# ----------------------------------------------------------------------------

variable "business_instance_class" {
  description = "business cluster の instance class。"
  type        = string
}

variable "business_instance_count" {
  description = "business cluster の instance 数。"
  type        = number
}

# ----------------------------------------------------------------------------
# common-base cluster (identity-broker / audit / notification / workflow / analytics / integration-hub)
# ----------------------------------------------------------------------------

variable "common_instance_class" {
  description = "common-base cluster の instance class。"
  type        = string
}

variable "common_instance_count" {
  description = "common-base cluster の instance 数。"
  type        = number
}

# ----------------------------------------------------------------------------
# 共通設定 (env 単位で切替)
# ----------------------------------------------------------------------------

variable "deletion_protection" {
  description = "全 cluster 共通の deletion_protection。 prod = true 必須。"
  type        = bool
}

variable "skip_final_snapshot" {
  description = "全 cluster 共通の skip_final_snapshot。 dev のみ true。"
  type        = bool
}

variable "backup_retention_period" {
  description = "全 cluster 共通の backup 保持日数。 dev=1, staging=7, prod=35。"
  type        = number
}

variable "performance_insights_enabled" {
  description = "Performance Insights 有効化。 dev = false (cost)、 staging/prod = true。"
  type        = bool
}
