output "user_pool_id" {
  description = "Cognito User Pool ID(例: ap-northeast-1_xxxxxxxxx)。"
  value       = aws_cognito_user_pool.main.id
}

output "user_pool_arn" {
  description = "Cognito User Pool の ARN。"
  value       = aws_cognito_user_pool.main.arn
}

output "issuer_uri" {
  description = <<-EOT
    OIDC issuer URI。 web app の `VITE_OIDC_AUTHORITY` および Identity Broker の
    `FEDERATION_ISSUER_URI` に注入する。
  EOT
  value       = "https://cognito-idp.${var.region}.amazonaws.com/${aws_cognito_user_pool.main.id}"
}

output "jwks_uri" {
  description = <<-EOT
    JWKS URI。 Identity Broker の `FEDERATION_JWKS_URI` に注入し、 Nimbus が remote
    fetch して access token の signature 検証に使う。
  EOT
  value       = "https://cognito-idp.${var.region}.amazonaws.com/${aws_cognito_user_pool.main.id}/.well-known/jwks.json"
}

output "hosted_ui_base_url" {
  description = "Hosted UI のベース URL。 web app の `VITE_OIDC_AUTHORITY` ではなく login redirect の起点として参照。"
  value       = "https://${aws_cognito_user_pool_domain.hosted_ui.domain}.auth.${var.region}.amazoncognito.com"
}

output "saml_acs_url" {
  description = <<-EOT
    SAML IdP 側に登録する Reply URL(Assertion Consumer Service URL)。 IdP 設定画面で
    本値を貼る(SAMLResponse の送信先)。
  EOT
  value       = "https://${aws_cognito_user_pool_domain.hosted_ui.domain}.auth.${var.region}.amazoncognito.com/saml2/idpresponse"
}

output "saml_entity_id" {
  description = "SAML IdP 側に登録する Identifier (Entity ID)。"
  value       = "urn:amazon:cognito:sp:${aws_cognito_user_pool.main.id}"
}

output "app_client_ids" {
  description = <<-EOT
    各業態 web app 用 App Client ID の map。 web-<業態> をキーにして、 各 web app の
    K8s ConfigMap に `VITE_OIDC_CLIENT_ID` として注入する。
  EOT
  value = {
    for name, client in aws_cognito_user_pool_client.web_apps :
    name => client.id
  }
}
