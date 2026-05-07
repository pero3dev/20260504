# Pact Broker(local dev / 本番 EKS)

ADR-0019 Phase 3 で local 用 docker-compose を導入。 ADR-0021 で本番ホスティングを **EKS self-host on Aurora-C** に確定。 本ディレクトリには:

```
infra/pact-broker/
├── docker-compose.pact-broker.yml  # local dev 用(ADR-0019 Phase 3)
├── README.md                       # 本ファイル
├── k8s/                            # 本番デプロイ manifests(ADR-0021 Phase 1〜2.5)
│   ├── kustomization.yaml
│   ├── namespace.yaml
│   ├── serviceaccount.yaml         # IRSA 経由
│   ├── configmap.yaml              # Pact Broker 非機密設定
│   ├── configmap-nginx.yaml        # nginx sidecar config(Phase 2.5、 auth bridge)
│   ├── secret.example.yaml         # 機密 (External Secrets Operator で生成)
│   ├── deployment.yaml             # 2 replicas + nginx-auth-bridge sidecar(Phase 2.5)
│   ├── service.yaml                # ClusterIP、 api(9292)+ ui(9293) の 2 ポート
│   ├── ingress-ui.yaml             # ALB internal + Cognito SSO → sidecar 9293(Phase 2/2.5)
│   ├── ingress-api.yaml            # ALB internal + Basic Auth 直結 → 9292(Phase 1)
│   ├── hpa.yaml                    # 2-3 replicas autoscale
│   └── networkpolicy.yaml          # ingress: ALB only / egress: Aurora + DNS
├── argocd/
│   └── application.yaml            # ArgoCD Application(GitOps)
└── db/
    └── 001-create-pact-broker-db.sql  # Aurora-C 内 DB 切り出し SQL
```

Consumer-driven contract test の publish 先と、 `can-i-deploy` 判定のための contract repository を兼ねる。

## 起動

```bash
docker compose -f infra/pact-broker/docker-compose.pact-broker.yml up -d
```

UI: <http://localhost:9292>(Basic Auth: `pact_user` / `pact_password`)

## 停止

```bash
docker compose -f infra/pact-broker/docker-compose.pact-broker.yml down
# volume も消す場合:
docker compose -f infra/pact-broker/docker-compose.pact-broker.yml down -v
```

## 使い方

### Consumer 側 — 契約を publish

inventory-core で Consumer Pact test を実行 → `target/pacts/*.json` 生成 → Broker に publish。

```bash
# 1. consumer test を走らせて pact 生成
mvn -pl services/inventory-core -Dtest='*ConsumerPactTest' test

# 2. Broker に publish(Broker URL/credential は env / system property で渡す)
mvn -pl services/inventory-core pact:publish \
  -Dpact.broker.url=http://localhost:9292 \
  -Dpact.broker.username=pact_user \
  -Dpact.broker.password=pact_password \
  -Dpact.consumer.version=$(git rev-parse --short HEAD) \
  -Dpact.consumer.tags=$(git branch --show-current)
```

### Provider 側 — Broker から契約を取得して verify

`PACT_BROKER_URL` 環境変数が設定されていれば各 Provider Pact test は自動で Broker を読みに行く(後述の Phase 3 実装で追加)。 未設定なら従来どおり `target/pacts/` 配下の PactFolder から読む。

```bash
PACT_BROKER_URL=http://localhost:9292 \
PACT_BROKER_USERNAME=pact_user \
PACT_BROKER_PASSWORD=pact_password \
mvn -pl services/wholesale -Dtest='*ProviderPactTest' \
  -Dpact.providerVerifier.enabled=true test
```

### can-i-deploy

```bash
docker run --rm pactfoundation/pact-cli:latest \
  broker can-i-deploy \
  --broker-base-url http://host.docker.internal:9292 \
  --broker-username pact_user \
  --broker-password pact_password \
  --pacticipant inventory-core \
  --version $(git rev-parse --short HEAD) \
  --to-environment production
```

## 認証情報(local)

Local dev のデフォルト credential は **`pact_user` / `pact_password`** で固定(`docker-compose.pact-broker.yml`)。

## 本番デプロイ手順(ADR-0021 Phase 1)

### 前提

- EKS prod クラスタ(ADR-0013)が存在し、 ArgoCD admin namespace から GitOps 管理されている
- Aurora-C(common-base、 ADR-0005)が存在し、 master credential で接続可能
- AWS Secrets Manager + ExternalSecretsOperator が cluster に導入済 / SealedSecrets でも可
- AWS Load Balancer Controller が導入済(ALB Ingress 用)
- Route53 で `pact-broker.internal.example.com` の internal hosted zone 設定済
- ACM 証明書(`pact-broker.internal.example.com`)が ap-northeast-1 region に発行済

### Step 1 — Aurora-C に DB 切り出し

`db/001-create-pact-broker-db.sql` を Aurora-C master account で 1 回実行。 password は AWS Secrets Manager の secret(例: `prod/pact-broker/db`)から取得して埋める。

```bash
psql -h aurora-c-cluster-prod.internal.example.com -U master -d postgres \
  -f infra/pact-broker/db/001-create-pact-broker-db.sql
```

### Step 2 — IAM Role for ServiceAccount(IRSA)

ExternalSecretsOperator から AWS Secrets Manager の secret を読むための IAM Role を作る:

```bash
# eksctl の場合
eksctl create iamserviceaccount \
  --name pact-broker \
  --namespace pact-broker \
  --cluster prod \
  --attach-policy-arn arn:aws:iam::ACCOUNT_ID:policy/PactBrokerSecretsAccess \
  --approve --override-existing-serviceaccounts
```

Policy は AWS Secrets Manager の `prod/pact-broker/*` リソースへの `secretsmanager:GetSecretValue` のみ許可。

### Step 3 — manifests の環境 patch

`k8s/` 配下の以下 placeholder を実値に書き換える(本番 PR で行う):

| ファイル | 書き換え対象 |
|---|---|
| `serviceaccount.yaml` | `arn:aws:iam::ACCOUNT_ID:role/pact-broker-prod` を実 ARN に |
| `configmap.yaml` | `aurora-c-cluster.internal.example.com` を実 host に / `pact-broker.internal.example.com` を実 host に |
| `ingress-ui.yaml` | `alb.ingress.kubernetes.io/certificate-arn` を実 ACM ARN に / `host` を実 host に / Cognito UserPoolArn / UserPoolClientId / UserPoolDomain(Phase 2) |
| `ingress-api.yaml` | `alb.ingress.kubernetes.io/certificate-arn` を実 ACM ARN に / `host` を実 host に |
| `networkpolicy.yaml` | `10.0.0.0/8` を実 VPC CIDR に |

将来的に kustomize overlay(`overlays/prod/`)で自動化する。 本フェーズでは 1 環境(prod)分だけ用意。

### Step 4 — ExternalSecret で Secret を生成

ExternalSecretsOperator の `ExternalSecret` リソースを別途作成し、 AWS Secrets Manager から:

- `PACT_BROKER_DATABASE_PASSWORD`
- `PACT_BROKER_BASIC_AUTH_USERNAME` / `PACT_BROKER_BASIC_AUTH_PASSWORD`
- `PACT_BROKER_BASIC_AUTH_READ_ONLY_USERNAME` / `PACT_BROKER_BASIC_AUTH_READ_ONLY_PASSWORD`

を K8s Secret `pact-broker` に同期する。 マニフェストは Platform Team の secret 管理リポジトリに置く想定(本リポジトリには含めない)。

### Step 5 — ArgoCD Application 登録

```bash
kubectl apply -f infra/pact-broker/argocd/application.yaml
```

ArgoCD UI で `pact-broker` Application が `Synced` / `Healthy` になるまで待つ。

### Step 6 — DNS

Route53 で `pact-broker.internal.example.com` を ALB の DNS 名 alias で登録。 internal-only なので private hosted zone のみ。

### Step 7 — GitHub Actions secret 設定

GitHub repository → Settings → Secrets and variables → Actions に以下を登録:

- `PACT_BROKER_URL` = `https://pact-broker.internal.example.com`
- `PACT_BROKER_USERNAME` = Step 4 の Basic Auth user
- `PACT_BROKER_PASSWORD` = Step 4 の Basic Auth password

これで `.github/workflows/pact-broker.yml` の dormant 状態が解除され、 main push / PR トリガで Broker への publish + provider-verify + can-i-deploy が稼働開始。

### Step 8 — 動作確認

```bash
# 1. local 端末から API ALB に届くか(VPN / Bastion 経由、 Basic Auth)
curl -u <user>:<pass> https://pact-broker-api.internal.example.com/diagnostic/status/heartbeat

# 2. 適当な PR を立てて GitHub Actions で publish + verify が走るか
# 3. ArgoCD UI で `pact-broker` Application が `Synced` / `Healthy`
# 4. `kubectl -n pact-broker get pods` で 2 replicas が Running
```

## ADR-0021 Phase 2 — Cognito SSO 連携(人間 UI 経路)

Phase 1 で Basic Auth ベースで立ち上げた後、 Phase 2 で人間 UI アクセスを Cognito SSO に集約する。 CI(machine-to-machine)は引き続き Basic Auth で `pact-broker-api.internal.example.com` 経由。

### 構成

| 経路 | hostname | auth | 用途 |
|---|---|---|---|
| UI(人間)| `pact-broker.internal.example.com` | ALB → Cognito SSO | エンジニアの contract 閲覧 |
| API(CI)| `pact-broker-api.internal.example.com` | Basic Auth 直結 | GitHub Actions / Maven plugin |

`alb.ingress.kubernetes.io/group.name: pact-broker` で **同一 ALB を 2 ingress で共有**。 hostname だけが違う(host header で listener rule が分岐)。

### Step 1 — Cognito User Pool / App Client を identity-broker から取得

ADR-0007 で identity-broker サービスが Cognito User Pool を管轄しているため、 同じ User Pool に Pact Broker 用の **App Client** を 1 つ追加する。

App Client 設定:

```yaml
ClientName: pact-broker-ui
GenerateSecret: true                       # ALB が client_secret を必要とする
AllowedOAuthFlows: [code]                  # authorization code grant
AllowedOAuthScopes: [openid, profile, email]
CallbackURLs:
  - https://pact-broker.internal.example.com/oauth2/idpresponse
LogoutURLs:
  - https://pact-broker.internal.example.com/
SupportedIdentityProviders:                # SAML 連携(ADR-0007)
  - COGNITO
  - <Corporate-IdP-Name>
```

Cognito Domain: `pact-broker-prod`(global unique、 例: `pact-broker-prod.auth.ap-northeast-1.amazoncognito.com`)。 Pool の domain 設定で予約。

### Step 2 — `ingress-ui.yaml` の placeholder を実値に

```yaml
alb.ingress.kubernetes.io/auth-idp-cognito: |
  {
    "UserPoolArn": "arn:aws:cognito-idp:ap-northeast-1:<ACCOUNT_ID>:userpool/<POOL_ID>",
    "UserPoolClientId": "<APP_CLIENT_ID>",
    "UserPoolDomain": "pact-broker-prod"
  }
```

ArgoCD が同期して新 Ingress 設定を ALB Controller に反映、 ALB に Cognito 認証アクションが追加される。

### Step 3 — Pact Broker 側の Basic Auth 互換性

Phase 2 では Pact Broker は **Basic Auth 構成のまま**。 ALB Cognito auth を通過したリクエストは `X-Amzn-Oidc-Data` JWT ヘッダ付きで Pact Broker に転送されるが、 Pact Broker は OIDC を理解しないので Basic Auth credential を別途要求する(ブラウザの Basic Auth ダイアログ)。

→ **UX 上、SSO + Basic Auth の "二段認証" になる**。 Cognito で社内 ID 確認、 Basic Auth で Pact Broker 上の権限(read-only / write)を切り替える。

完全シームレス SSO(Cognito JWT → Basic Auth 自動注入)は **Phase 2.5 候補** として後送り。 oauth2-proxy sidecar を Pact Broker pod に同居させるか、 ALB Lambda authorizer で実現する。 Phase 2 のスコープでは **「Cognito SSO で組織レベルアクセス制御を強制 + Pact Broker Basic Auth で role 管理」** で十分とする。

### Step 4 — DNS 追加

Route53 private hosted zone に **`pact-broker.internal.example.com`** + **`pact-broker-api.internal.example.com`** の 2 レコードを ALB DNS 名 alias で登録。 同じ ALB を共有するので DNS targetは同一。

### Step 5 — GitHub Actions secret 切替

CI は **API hostname 経由**になるので、 GitHub repository → Settings → Secrets で:

- `PACT_BROKER_URL` = `https://pact-broker-api.internal.example.com` ← 旧値から書き換え

`PACT_BROKER_USERNAME` / `PACT_BROKER_PASSWORD` は変えない(Basic Auth 直結のまま)。

### Step 6 — 動作確認

```bash
# UI(人間): ブラウザで開いて Cognito sign-in が出るか
open https://pact-broker.internal.example.com/

# API(CI): 引き続き Basic Auth で curl が通るか
curl -u <user>:<pass> https://pact-broker-api.internal.example.com/diagnostic/status/heartbeat

# GitHub Actions の pact-broker.yml workflow が緑のまま走るか
```

## ADR-0021 Phase 2.5 — 完全シームレス SSO(nginx auth bridge)

Phase 2 の二段認証 UX(Cognito + Basic Auth ダイアログ)を解消する。 Pact Broker pod に nginx sidecar を同居させ、 ALB Cognito auth で認証されたリクエストに Pact Broker の Basic Auth credential を自動注入する。

### 構成

```
人間 (UI)
  ↓ HTTPS
ALB (Cognito auth)
  ↓ X-Amzn-Oidc-Data 付き
nginx-auth-bridge sidecar (pod port 9293)
  ↓ Authorization: Basic <read-only-cred> 注入
Pact Broker container (pod port 9292)

CI (API)
  ↓ HTTPS + Authorization: Basic <ci-cred>
ALB (Cognito auth bypass、 別 hostname)
  ↓ そのまま
Pact Broker container (pod port 9292)  ← sidecar 経由しない
```

### 主な仕掛け

- **nginx 公式 image の template 機能**(`/etc/nginx/templates/*.template` を起動時に envsubst 展開)で、 `${PACT_BROKER_INJECT_BASIC_AUTH_B64}` を Secret から差し込む。
- **Service が 2 ポート公開**: `api` (port 80 → pod 9292) と `ui` (port 81 → pod 9293)。 各 Ingress が named port で振り分ける。
- **注入する credential は read-only**: Phase 3 の「UI を社内全員に read-only 公開」と整合。 write 操作は CI(API hostname)経由でのみ行う。
- **client が Authorization ヘッダを送ってきた場合は尊重**: nginx config の `if ($http_authorization = "")` で分岐し、 既にヘッダがあればそれを upstream に転送。 デバッグや手動 write が必要な場合の escape hatch。

### Step 1 — ExternalSecret 拡張

`PACT_BROKER_INJECT_BASIC_AUTH_B64` を AWS Secrets Manager から K8s Secret に同期するよう、 ExternalSecret マニフェストを 1 行追加:

```yaml
- secretKey: PACT_BROKER_INJECT_BASIC_AUTH_B64
  remoteRef:
    key: prod/pact-broker/inject-basic-auth-b64
```

Secrets Manager 側の値は:

```bash
echo -n "${PACT_BROKER_BASIC_AUTH_READ_ONLY_USERNAME}:${PACT_BROKER_BASIC_AUTH_READ_ONLY_PASSWORD}" | base64
# → cmVhZGVyOnNlY3JldA== みたいな
```

`PACT_BROKER_BASIC_AUTH_READ_ONLY_USERNAME / PASSWORD` と整合する base64 を保存。

### Step 2 — ArgoCD で sync

`infra/pact-broker/k8s/configmap-nginx.yaml` + `deployment.yaml`(sidecar 追加)+ `service.yaml`(2 ポート化)+ `ingress-*.yaml`(named port 化)が PR で merge されると、 ArgoCD が同期して新構成へ rolling update。

### Step 3 — 動作確認

```bash
# UI: ブラウザで Cognito sign-in 1 回 → Pact Broker が即時表示(Basic Auth ダイアログ無し)
open https://pact-broker.internal.example.com/

# API: 引き続き Basic Auth 直結
curl -u <user>:<pass> https://pact-broker-api.internal.example.com/diagnostic/status/heartbeat

# Sidecar 健全性
kubectl -n pact-broker get pods
# pact-broker-xxxxx 2/2 Running ← 2/2 が pact-broker + nginx-auth-bridge
kubectl -n pact-broker logs deployment/pact-broker -c nginx-auth-bridge | head
```

### 設計選択の根拠

なぜ oauth2-proxy ではなく nginx を選んだか:

- **ALB が既に Cognito auth を済ませている**ため、 sidecar 側で OIDC handshake をやり直す必要がない。 oauth2-proxy はフルの OIDC client だが、 そこまで要らない。
- **nginx は超軽量**(20m CPU / 32Mi memory)、 image 5MB 程度の alpine。 oauth2-proxy は OIDC token cache / session store 等を持つので 50-100MB。
- **設定が tiny**(20 行の nginx.conf)で運用負担小。

ALB が JWT 発行(Cognito 連携)+ session 維持、 sidecar が credential 注入、 Pact Broker は Basic Auth のまま、 という **責務分離** が綺麗に決まる。

## 参照

- ADR-0019: Pact による Consumer-driven 契約テストの段階導入
- ADR-0021: Pact Broker 本番ホスティング(EKS self-host on Aurora-C)
- ADR-0005: Database ownership(Aurora-C を共有する根拠)
- ADR-0013: EKS topology(namespace per service の根拠)
- 上流ドキュメント: <https://docs.pact.io/pact_broker/docker_images/pactfoundation>
