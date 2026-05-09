# 社内 SAML IdP(Azure AD / Okta / Google Workspace 等)を Cognito に登録する。
# Hosted UI からは本 IdP 経由の認証だけが許可される(App Client の supported_identity_providers
# に CorporateSAML のみを指定するため、 Cognito 内 username/password 直接認証は出ない)。

resource "aws_cognito_identity_provider" "corporate_saml" {
  user_pool_id  = aws_cognito_user_pool.main.id
  provider_name = var.saml_idp_name
  provider_type = "SAML"

  provider_details = {
    MetadataURL = var.saml_metadata_url
    # SAMLResponse の RelayState を受け入れて Cognito 側 callback に渡す既定値。
    IDPSignout = "false"
  }

  attribute_mapping = var.saml_attribute_mapping
}
