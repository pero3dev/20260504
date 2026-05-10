# Cognito User Pool + SAML Federation — Terraform IaC

F2 phase F で AWS CLI ランブック(親 `infra/cognito/README.md`)を Terraform に置換した。 CLI 手順は手動 / 教育用に残し、 prod は本ディレクトリの IaC を使う。

## 構成

```
infra/cognito/terraform/
├── versions.tf               # Terraform / aws provider バージョン pin + S3 backend placeholder
├── variables.tf              # 入力変数(region / env / pool 名 / SAML metadata URL / app_clients)
├── main.tf                   # aws_cognito_user_pool + aws_cognito_user_pool_domain
├── saml-idp.tf               # aws_cognito_identity_provider(SAML)
├── app-clients.tf            # aws_cognito_user_pool_client × 4(web 業態ごと)
├── outputs.tf                # issuer_uri / jwks_uri / app_client_ids / saml_acs_url 等
├── terraform.tfvars.example  # tfvars テンプレ
└── README.md                 # 本ファイル
```

## 操作

```bash
cd infra/cognito/terraform

# 1. backend 配置(初回のみ)。 `versions.tf` の backend "s3" を有効化し
#    bucket / DynamoDB lock table を別途作成する(別タスク `infra/audit-s3/` 参照)。
terraform init

# 2. tfvars を env ごとに用意。 prod 例:
cp terraform.tfvars.example terraform.prod.tfvars
$EDITOR terraform.prod.tfvars   # SAML metadata URL / app callback URL を実値に

# 3. plan(差分確認)。 prod は SecOps レビュー必須。
terraform plan -var-file=terraform.prod.tfvars -out=plan.bin

# 4. apply。 destructive な変更(User Pool 削除 / 名前変更)は手動承認 step に分けること。
terraform apply plan.bin

# 5. outputs を Identity Broker / web app の K8s ConfigMap に注入(下記参照)。
terraform output
```

## 出力 → 配布

| Output                | 用途                                         | 注入先                                   |
|-----------------------|----------------------------------------------|------------------------------------------|
| `issuer_uri`          | OIDC issuer                                  | `VITE_OIDC_AUTHORITY`(4 web app)        |
| `issuer_uri`          | IB 側 federation issuer                      | `FEDERATION_ISSUER_URI`(IB Secret)      |
| `jwks_uri`            | IB 側 JWKS fetch                             | `FEDERATION_JWKS_URI`(IB Secret)        |
| `app_client_ids[*]`   | 各 web app の Client ID                      | `VITE_OIDC_CLIENT_ID`(各 web app env)   |
| `app_client_ids` 全値 | IB 側 audience 検証(CSV 列挙、 multi-client 対応) | `FEDERATION_AUDIENCE`(IB Secret、 A5 follow-up²⁹) |
| `federation_audience_csv` | 上の CSV joined 形(map 値を `,` 連結) | `FEDERATION_AUDIENCE` 直注入(A5 follow-up³⁰) |
| `hosted_ui_base_url`  | login redirect 起点                          | (web app は authority から自動構築するので注入不要) |
| `saml_acs_url`        | SAML IdP の Reply URL                        | IdP 管理画面に手動登録                    |
| `saml_entity_id`      | SAML IdP の Entity ID                        | IdP 管理画面に手動登録                    |

## SAML IdP 側設定(Terraform 範囲外)

Cognito 側は本 IaC で完結するが、 IdP(Azure AD / Okta / Google Workspace 等)側の登録は管理画面 / API が IdP ごとに違うため Terraform 化は次フェーズ。 必要な値:

- **Identifier (Entity ID)**: `terraform output saml_entity_id`(`urn:amazon:cognito:sp:<pool-id>`)
- **Reply URL (ACS)**: `terraform output saml_acs_url`
- **NameID format**: `urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress`
- **Attribute statement**: 少なくとも `email` claim を含める(`saml_attribute_mapping` で参照)

## drift 検知

prod は CI で `terraform plan -detailed-exitcode` を週次実行し、 0 以外なら Slack 通知する想定(別タスク)。 Cognito 側を AWS Console から直接変更すると IaC との drift になるため、 緊急時除き Terraform 経由でのみ変更する。

## 既知の制約

- **SAML metadata URL** は public な HTTPS が必要(Cognito が fetch するため)。 IdP が private 配布ならローカル DL → S3 公開バケット経由など別手段。
- **App Client** は SAML 1 IdP のみ。 業態混在の SP 認証(別 IdP との並行利用)が必要なら別 User Pool を立てる。
- **MFA** は OFF(SAML IdP 側で実施前提)。 step-up MFA 要件が顕在化したら `mfa_configuration = "ON"` + custom auth flow 検討。
