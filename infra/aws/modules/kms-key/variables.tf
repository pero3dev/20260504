variable "alias_name" {
  description = "KMS alias 名 (例: alias/dev-aurora)。 alias/ prefix を含めて指定する。"
  type        = string

  validation {
    condition     = startswith(var.alias_name, "alias/")
    error_message = "alias_name は 'alias/' で始める必要がある (AWS 仕様)。"
  }
}

variable "description" {
  description = "KMS key の description。 用途と env が分かる文字列を推奨。"
  type        = string
}

variable "deletion_window_in_days" {
  description = <<-EOT
    削除予約後の待機日数 (7〜30)。 削除実行前に待機期間中ならキャンセル可能。
    本番の暗号化 key 削除は復旧不能なので default 30 (最大、 安全側)。
  EOT
  type        = number
  default     = 30

  validation {
    condition     = var.deletion_window_in_days >= 7 && var.deletion_window_in_days <= 30
    error_message = "deletion_window_in_days は 7〜30 の整数。"
  }
}

variable "enable_key_rotation" {
  description = "annual auto-rotation を有効化するか。 PCI-DSS / J-SOX 等の compliance 要件で必須。"
  type        = bool
  default     = true
}

variable "additional_principals" {
  description = <<-EOT
    account root に加えて kms:* を許可する IAM principal (Role / User) ARN のリスト。
    AWS service principal (例: rds.amazonaws.com) は IAM grant 経由で別途処理されるためここには含めない。
    v1 では空のままで運用 (account root + IAM 経由で deploy role が AdminAccess を持つので十分)。
  EOT
  type        = list(string)
  default     = []
}

variable "tags" {
  description = "追加 tag。 stack 側 default_tags と merge される。"
  type        = map(string)
  default     = {}
}
