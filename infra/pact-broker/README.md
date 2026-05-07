# Pact Broker(local dev / 本番 EKS)

ADR-0019 Phase 3 で local 用 docker-compose を導入。 ADR-0021 で本番ホスティングを **EKS self-host on Aurora-C** に確定。 本ディレクトリには:

```
infra/pact-broker/
├── docker-compose.pact-broker.yml  # local dev 用(ADR-0019 Phase 3)
├── README.md                       # 本ファイル
├── k8s/                            # 本番デプロイ manifests(ADR-0021 Phase 1)
│   ├── kustomization.yaml
│   ├── namespace.yaml
│   ├── serviceaccount.yaml         # IRSA 経由
│   ├── configmap.yaml              # 非機密設定
│   ├── secret.example.yaml         # 機密 (External Secrets Operator で生成)
│   ├── deployment.yaml             # 2 replicas
│   ├── service.yaml                # ClusterIP
│   ├── ingress.yaml                # ALB internal-only
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
| `ingress.yaml` | `alb.ingress.kubernetes.io/certificate-arn` を実 ACM ARN に / `host` を実 host に |
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
# 1. local 端末から ALB に届くか(VPN / Bastion 経由)
curl -u <user>:<pass> https://pact-broker.internal.example.com/diagnostic/status/heartbeat

# 2. 適当な PR を立てて GitHub Actions で publish + verify が走るか
# 3. ArgoCD UI で `pact-broker` Application が `Synced` / `Healthy`
# 4. `kubectl -n pact-broker get pods` で 2 replicas が Running
```

## 参照

- ADR-0019: Pact による Consumer-driven 契約テストの段階導入
- ADR-0021: Pact Broker 本番ホスティング(EKS self-host on Aurora-C)
- ADR-0005: Database ownership(Aurora-C を共有する根拠)
- ADR-0013: EKS topology(namespace per service の根拠)
- 上流ドキュメント: <https://docs.pact.io/pact_broker/docker_images/pactfoundation>
