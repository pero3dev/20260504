variable "environment" {
  description = "dev / staging / prod。 bucket 名 prefix と Environment tag に注入。"
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment は dev / staging / prod のいずれか。"
  }
}

variable "region" {
  description = "AWS リージョン。 audit S3 の primary region。 CRR 先 (大阪) は別 stack で扱う。"
  type        = string
  default     = "ap-northeast-1"
}

variable "bucket_name_override" {
  description = <<-EOT
    バケット名上書き。 default は `inventory-platform-<env>-audit`。
    S3 名前空間衝突時に上書きする (e.g. account 番号付与)。
  EOT
  type        = string
  default     = ""
}

variable "object_lock_retention_days" {
  description = "Object Lock Compliance mode retention 日数。 ADR-0008 の J-SOX 要件で 365 日。"
  type        = number
  default     = 365
}

variable "athena_projection_date_range" {
  description = <<-EOT
    Glue table の partition projection date 範囲 (`<from>,NOW`)。
    本番は anchor 計算開始日 (2026-01-01) を超えないこと、
    過去過ぎる範囲は projection 計算 cost を増やす。
  EOT
  type        = string
  default     = "2026-01-01,NOW"
}
