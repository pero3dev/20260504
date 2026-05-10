variable "environment" {
  description = "dev / staging / prod。 cluster 名 prefix と Environment tag に注入。"
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

# MSK Serverless は broker sizing / storage / config 引数を持たない (auto-scaling)。
# per-env 差別化が必要なのは subnet group / SG (= vpc) と環境名のみ。
