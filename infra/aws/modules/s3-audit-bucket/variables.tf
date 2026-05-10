variable "bucket_name" {
  description = <<-EOT
    バケット名。 命名規則 `inventory-platform-<env>-audit` を推奨。
    S3 バケット名は global namespace なので衝突時は上書きで指定する想定。
  EOT
  type        = string
}

variable "kms_key_arn" {
  description = "audit S3 用 KMS CMK ARN。 kms stack の audit_s3_key_arn output を渡す想定。"
  type        = string
}

variable "object_lock_retention_days" {
  description = <<-EOT
    Object Lock Compliance mode の default retention 期間 (日数)。
    ADR-0008 では 365 日 (J-SOX 上の audit 保持期間)。
    Compliance mode は **短くする変更が不可**、 長くする変更のみ可能。
  EOT
  type        = number
  default     = 365
}

variable "lifecycle_expiration_days" {
  description = <<-EOT
    object 自動削除までの日数。 retention 365 日 + lifecycle 365 日で
    「retention 経過後すぐに削除」 の挙動になる。 retention 期限内は
    Compliance mode が削除を物理拒否するため、 lifecycle が早期発火しても無効。
  EOT
  type        = number
  default     = 365
}

variable "tags" {
  description = "追加 tag。 stack 側 default_tags と merge される。"
  type        = map(string)
  default     = {}
}
