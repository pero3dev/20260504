## Cognito User Pool + SAML Federation — 本番デプロイランブック(F2 phase C / F)

> **prod は Terraform IaC を使う**: F2 phase F で [`./terraform/`](./terraform/) に IaC を整備済。 本ファイルの AWS CLI 手順は手動デプロイ用 / 教育目的で残す。 prod 変更は必ず `terraform plan -var-file=terraform.<env>.tfvars` を経由する。

Identity Broker `POST /v1/auth/exchange` が verify する access token を発行する側の構成。 web app は Cognito Hosted UI 経由で SAML IdP(Azure AD / Okta / Google Workspace 等)に直接 redirect し、 Cognito が SAML assertion → OIDC token に変換する。

### 全体フロー

```
[web app] ──signinRedirect──▶ [Cognito Hosted UI] ──SAML AuthnRequest──▶ [社内 IdP]
                                                                              │
                                                            ◀───SAML Response┘
[web app] ◀──code─── [Cognito Hosted UI]
   │
   │ POST /oauth2/token (PKCE)
   ▼
[Cognito] ──access_token───▶ [web app] ──POST /v1/auth/exchange──▶ [Identity Broker]
                                                                          │
                                                              session_token + accessibleTenants[]
                                                                          │
                                                            ◀─────────────┘
[web app] ──POST /v1/auth/tenant-sessions──▶ [Identity Broker] → tenant-scoped JWT
[web app] ──Bearer ${access_token}──▶ [BFF (Apollo)] ── verify (JWKS) ──▶ [backend]
```

### 構成リソース

```
infra/cognito/
├── README.md                 # 本ファイル(AWS CLI 手順 / 教育用)
├── attribute-mapping.json    # SAML assertion attribute → Cognito user attribute(CLI 用)
└── terraform/                # ★ prod は本ディレクトリの IaC を使う(F2 phase F 整備)
    ├── README.md
    ├── versions.tf
    ├── variables.tf
    ├── main.tf
    ├── saml-idp.tf
    ├── app-clients.tf
    ├── outputs.tf
    └── terraform.tfvars.example
```

prod は `terraform/` の IaC が SoR。 本 README の AWS CLI 手順は手動 / 教育用に残しておく(IaC が動かない緊急時のフォールバック用にも有効)。

### Step 1. User Pool を作成

```bash
export REGION="ap-northeast-1"
export POOL_NAME="inventory-platform-prod"

aws cognito-idp create-user-pool \
  --pool-name "$POOL_NAME" \
  --region "$REGION" \
  --policies '{"PasswordPolicy":{"MinimumLength":12,"RequireUppercase":true,"RequireLowercase":true,"RequireNumbers":true,"RequireSymbols":true}}' \
  --schema '[{"Name":"email","Required":true,"Mutable":false,"AttributeDataType":"String"}]' \
  --username-attributes email \
  --auto-verified-attributes email
```

`POOL_ID` を控える(`ap-northeast-1_xxxxxxxxx` 形式)。

### Step 2. User Pool Domain(Hosted UI 用)

```bash
export DOMAIN_PREFIX="inventory-platform-prod"   # 全 Cognito 内で一意

aws cognito-idp create-user-pool-domain \
  --user-pool-id "$POOL_ID" \
  --domain "$DOMAIN_PREFIX" \
  --region "$REGION"
```

Hosted UI URL: `https://${DOMAIN_PREFIX}.auth.${REGION}.amazoncognito.com`

### Step 3. SAML IdP を登録

社内 SAML IdP のメタデータ XML(Azure AD なら「フェデレーション メタデータ XML」)を S3 にアップロード or URL で参照可能にする。

```bash
aws cognito-idp create-identity-provider \
  --user-pool-id "$POOL_ID" \
  --provider-name "CorporateSAML" \
  --provider-type SAML \
  --provider-details "MetadataURL=https://login.example.com/federationmetadata.xml" \
  --attribute-mapping file://infra/cognito/attribute-mapping.json \
  --region "$REGION"
```

`attribute-mapping.json` で SAML claim を Cognito attribute にマップ:

```json
{
  "email": "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress",
  "given_name": "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname",
  "family_name": "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname"
}
```

### Step 4. App Client を作成(web app 用)

```bash
aws cognito-idp create-user-pool-client \
  --user-pool-id "$POOL_ID" \
  --client-name "web-retail-ec" \
  --no-generate-secret \
  --supported-identity-providers "CorporateSAML" \
  --callback-urls "https://retail-ec.app.example.com/callback" \
  --logout-urls "https://retail-ec.app.example.com/" \
  --allowed-o-auth-flows code \
  --allowed-o-auth-scopes openid profile email \
  --allowed-o-auth-flows-user-pool-client \
  --explicit-auth-flows ALLOW_USER_SRP_AUTH ALLOW_REFRESH_TOKEN_AUTH \
  --region "$REGION"
```

各業態 web app 分繰り返す(`web-manufacturing` / `web-tpl` / `web-wholesale`)。 各 `CLIENT_ID` を控える。

### Step 5. SAML IdP 側の設定

Azure AD なら「エンタープライズ アプリケーション」→「シングル サインオン (SAML)」で:

| 項目 | 値 |
|---|---|
| Identifier (Entity ID) | `urn:amazon:cognito:sp:${POOL_ID}` |
| Reply URL (ACS) | `https://${DOMAIN_PREFIX}.auth.${REGION}.amazoncognito.com/saml2/idpresponse` |
| Sign-on URL | `https://${DOMAIN_PREFIX}.auth.${REGION}.amazoncognito.com/login?response_type=code&client_id=${CLIENT_ID}&redirect_uri=https://...` |

### Step 6. Identity Broker への配布値

各 web app に以下を Vite ビルド時 env として注入(K8s 経由 ConfigMap):

```
VITE_OIDC_AUTHORITY=https://cognito-idp.${REGION}.amazonaws.com/${POOL_ID}
VITE_OIDC_CLIENT_ID=<step 4 で控えた CLIENT_ID>
VITE_OIDC_REDIRECT_URI=https://<app-host>/callback
VITE_OIDC_POST_LOGOUT_REDIRECT_URI=https://<app-host>/
VITE_OIDC_SILENT_REDIRECT_URI=https://<app-host>/silent-renew.html
VITE_OIDC_SCOPE=openid profile email
```

Identity Broker には(K8s Secret 経由):

```
FEDERATION_ISSUER_URI=https://cognito-idp.${REGION}.amazonaws.com/${POOL_ID}
FEDERATION_JWKS_URI=https://cognito-idp.${REGION}.amazonaws.com/${POOL_ID}/.well-known/jwks.json
FEDERATION_SUBJECT_CLAIM=email           # access token に email claim を載せる設定が前提
FEDERATION_AUDIENCE_CLAIM=client_id      # Cognito access token は aud=client_id 構造
FEDERATION_AUDIENCE=<CLIENT_ID_1>,<CLIENT_ID_2>,...  # CSV で許可 client_id を列挙(A5 follow-up²⁹ で multi-client 対応)
```

### 検証手順

1. web app(dev) で Login button → Cognito Hosted UI → SAML IdP redirect → ログイン → 戻り
2. ブラウザの DevTools で `POST /v1/auth/exchange` のレスポンスが `sessionToken + accessibleTenants[]` を含むこと
3. tenant 選択後の access token を `jwt.io` でデコードし、 `tenant_id` / `roles` / `scopes` claim を確認
4. BFF が JWT verify pass し GraphQL クエリが成功

### 既知の制約

- 上記 CLI 手順は手動 / 教育用。 prod は [`./terraform/`](./terraform/) の IaC を使う(F2 phase F で整備済)
- Cognito Hosted UI の見た目はカスタムロゴ + 色のみ変更可能。 全画面差替えが要るなら Identity Broker 側に独自 SAML SP を立てる選択肢に切替
- SAML IdP 側 attribute statement に email を含めない構成だと user mapping 失敗。 Step 3 の attribute-mapping を IdP に揃えること
- JIT provisioning は F2 phase E で実装済(Identity Broker `platform.identity.federation.jit.enabled=true` で自動 user 作成)。 default tenant + role に固定の MVP 構成で、 マルチテナント振り分けは将来 SAML attribute mapping 拡張で対応
