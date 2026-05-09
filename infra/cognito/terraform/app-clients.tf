# 各業態 web app 用 App Client。 4 業態を for_each で同型に展開。
# refresh_token_validity を 30 日に伸ばし、 silent renew 経由で UI 側は連続使用可能。
# access_token_validity は 1 時間(短い、 silent renew で都度更新)。

resource "aws_cognito_user_pool_client" "web_apps" {
  for_each = var.app_clients

  name         = each.key
  user_pool_id = aws_cognito_user_pool.main.id

  # SAML 経由 federation のみ。 USER_PASSWORD_AUTH などは無効化(SAML IdP 側に集約)。
  supported_identity_providers = [aws_cognito_identity_provider.corporate_saml.provider_name]

  # PKCE + Authorization Code grant のみ。 implicit や client_credentials は禁止。
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_scopes                 = ["openid", "profile", "email"]

  callback_urls = each.value.callback_urls
  logout_urls   = each.value.logout_urls

  # SPA は client secret を持てないので無効化(`generate_secret = false`)。
  generate_secret = false

  # token 寿命:
  # - access_token: 60 分(IB 側 silent renew で更新)
  # - id_token: 60 分
  # - refresh_token: 30 日(SAML re-auth は SP 側で別途要件決め)
  access_token_validity  = 60
  id_token_validity      = 60
  refresh_token_validity = 30

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }

  # explicit_auth_flows: SAML 経由でも refresh_token 更新は必要。
  explicit_auth_flows = ["ALLOW_REFRESH_TOKEN_AUTH"]

  # SAML IdP 側で attribute statement に email を載せる構成が前提。
  # token に email を出して IB の subject-claim=email 解決と整合させる。
  read_attributes  = ["email", "given_name", "family_name", "email_verified"]
  write_attributes = ["email"]

  # 列挙攻撃対策(unknown user / wrong password で異なるレスポンスを返さない)。
  prevent_user_existence_errors = "ENABLED"
}
