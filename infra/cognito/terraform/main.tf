# User Pool 本体 + Hosted UI 用ドメイン。
# password policy は SAML 経由 federation の場合は実質使われないが、 Cognito 仕様で
# pool レベル設定が必要なので強い既定値を入れる。

resource "aws_cognito_user_pool" "main" {
  name = var.user_pool_name

  # email を username に使う(SAML claim 経由で設定)。
  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  # SAML federation だけ受ける構成だが Cognito の必須項目として強制ポリシーを置く。
  password_policy {
    minimum_length    = 12
    require_lowercase = true
    require_uppercase = true
    require_numbers   = true
    require_symbols   = true
  }

  # email スキーマは SAML attribute から書き戻されるので Mutable=false / Required=true。
  schema {
    name                     = "email"
    attribute_data_type      = "String"
    required                 = true
    mutable                  = false
    developer_only_attribute = false

    string_attribute_constraints {
      min_length = 3
      max_length = 254
    }
  }

  # SAML 経由ユーザの直接管理は SCIM / 手動で行う。 Cognito 側 admin 操作はログを残す。
  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  # MFA は SAML IdP 側で実施するので Cognito 側は OFF(将来 step-up MFA 要件で見直し)。
  mfa_configuration = "OFF"

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  tags = merge(var.tags, { Component = "user-pool" })
}

# Hosted UI ドメイン(SAML AuthnRequest を発射する側)。
resource "aws_cognito_user_pool_domain" "hosted_ui" {
  domain       = var.user_pool_domain_prefix
  user_pool_id = aws_cognito_user_pool.main.id
}
