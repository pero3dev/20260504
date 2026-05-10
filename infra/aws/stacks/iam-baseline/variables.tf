variable "region" {
  description = "AWS リージョン。 IAM はグローバルだが provider 設定としてリージョン指定が必要。"
  type        = string
  default     = "ap-northeast-1"
}

variable "github_org" {
  description = <<-EOT
    GitHub Actions OIDC trust policy で許可する GitHub organization / user 名。
    本リポジトリは pero3dev で hosting されており、 該当 org の repository 配下の
    workflow からのみ deploy role を assume できるよう trust 条件をスコープする。
  EOT
  type        = string
  default     = "pero3dev"
}

variable "github_repo" {
  description = <<-EOT
    GitHub Actions OIDC trust policy で許可する repository 名 (org 名は除く)。
    `repo:<github_org>/<github_repo>:*` の形で trust 条件に展開される。
  EOT
  type        = string
  default     = "20260504"
}

variable "tf_deploy_role_name" {
  description = "Terraform apply を実行する deploy role 名。 GitHub Actions が OIDC で assume する。"
  type        = string
  default     = "inventory-platform-tf-deploy"
}

variable "password_min_length" {
  description = "IAM user パスワード最小長。 NIST SP 800-63B の現行推奨値以上。"
  type        = number
  default     = 14
}

variable "password_max_age_days" {
  description = "IAM user パスワード有効期限 (日)。 90 日 rotation。"
  type        = number
  default     = 90
}

variable "password_reuse_prevention" {
  description = "直近 N 個のパスワード再利用禁止。 24 個保持。"
  type        = number
  default     = 24
}
