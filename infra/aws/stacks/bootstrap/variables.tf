variable "region" {
  description = "AWS リージョン。 ADR では ap-northeast-1 (東京) を default。 audit S3 の CRR 先 ap-northeast-3 (大阪) は別 stack で扱う。"
  type        = string
  default     = "ap-northeast-1"
}

variable "tfstate_bucket_name" {
  description = <<-EOT
    Terraform state を保管する S3 バケット名。 命名規則 `inventory-platform-<purpose>` に従い
    default は `inventory-platform-tfstate`。 単一 AWS account 内で全 stack × 全 env が
    本バケットを共有し、 state key (`aws/stacks/<stack>/<env>.tfstate`) で衝突を避ける。
  EOT
  type        = string
  default     = "inventory-platform-tfstate"
}

variable "tflock_table_name" {
  description = "DynamoDB lock table 名。 全 stack 共通の lock を取る。"
  type        = string
  default     = "inventory-platform-tflock"
}

variable "tfstate_kms_alias" {
  description = "tfstate 暗号化用 KMS key の alias。 alias/ prefix は AWS 側で必須。"
  type        = string
  default     = "alias/tfstate"
}

variable "tfstate_noncurrent_version_retention_days" {
  description = <<-EOT
    state バージョンの非current 保持日数。 versioning ON で過去 state を全保持すると S3 コストが
    線形増加するため lifecycle で削除する。 IaC drift 調査の実用上 90 日あれば十分。
  EOT
  type        = number
  default     = 90
}
