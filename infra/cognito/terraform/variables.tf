variable "region" {
  description = "AWS リージョン。 prod は ap-northeast-1(東京)。"
  type        = string
  default     = "ap-northeast-1"
}

variable "env_name" {
  description = "環境識別子(prod / staging / dev)。 リソース名 prefix に使う。"
  type        = string
  validation {
    condition     = contains(["prod", "staging", "dev"], var.env_name)
    error_message = "env_name は prod / staging / dev のいずれか。"
  }
}

variable "user_pool_name" {
  description = "Cognito User Pool 名(例: inventory-platform-prod)。"
  type        = string
}

variable "user_pool_domain_prefix" {
  description = <<-EOT
    Hosted UI のサブドメイン prefix(全 Cognito 内で一意)。
    最終 URL: https://<prefix>.auth.<region>.amazoncognito.com
  EOT
  type        = string
  validation {
    condition     = can(regex("^[a-z0-9-]{3,63}$", var.user_pool_domain_prefix))
    error_message = "domain prefix は ^[a-z0-9-]{3,63}$ に従う必要がある。"
  }
}

variable "saml_idp_name" {
  description = "Cognito 内での SAML IdP 識別名(IB アプリ側にも露出するので変更に注意)。"
  type        = string
  default     = "CorporateSAML"
}

variable "saml_metadata_url" {
  description = <<-EOT
    社内 SAML IdP の federation metadata XML の URL。
    Azure AD なら「フェデレーション メタデータ XML」、 Okta なら IdP metadata の HTTPS URL。
    ファイル直貼りしたい場合は本変数を空にし `saml_metadata_file` を別途追加(本 phase 未対応)。
  EOT
  type        = string
}

variable "saml_attribute_mapping" {
  description = <<-EOT
    SAML assertion attribute → Cognito user attribute の写像。
    キーが Cognito 側、 値が SAML claim URI。 attribute-mapping.json と同等。
  EOT
  type        = map(string)
  default = {
    email                 = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress"
    given_name            = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname"
    family_name           = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname"
    "custom:employee_id"  = "http://schemas.example.com/identity/claims/employeeid"
  }
}

variable "app_clients" {
  description = <<-EOT
    各業態 web app 用 App Client の入力。 名前は K8s deployment 名と揃える。
    4 業態 + Identity Broker 経由 federation の 1 client が原則。 Hosted UI からは
    SAML IdP のみ使用可能(USER_PASSWORD_AUTH 等の直接認証は無効)。
  EOT
  type = map(object({
    callback_urls = list(string)
    logout_urls   = list(string)
  }))

  default = {
    "web-retail-ec" = {
      callback_urls = ["https://retail-ec.app.example.com/callback"]
      logout_urls   = ["https://retail-ec.app.example.com/"]
    }
    "web-manufacturing" = {
      callback_urls = ["https://manufacturing.app.example.com/callback"]
      logout_urls   = ["https://manufacturing.app.example.com/"]
    }
    "web-tpl" = {
      callback_urls = ["https://tpl.app.example.com/callback"]
      logout_urls   = ["https://tpl.app.example.com/"]
    }
    "web-wholesale" = {
      callback_urls = ["https://wholesale.app.example.com/callback"]
      logout_urls   = ["https://wholesale.app.example.com/"]
    }
  }
}

variable "tags" {
  description = "全リソースに付ける共通タグ(Cost Allocation / SecOps の owner 識別用)。"
  type        = map(string)
  default = {
    Project = "inventory-platform"
    Module  = "identity-broker-federation"
  }
}
