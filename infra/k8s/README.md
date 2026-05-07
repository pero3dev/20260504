## Kubernetes manifests

各サービスの prod 配備時に使う K8s manifest 雛形。 helm / kustomize 化は後タスク。

| ディレクトリ | 用途 |
|---|---|
| `identity-broker/` | Identity Broker の federation 設定(Cognito issuer / JWKS / audience を ConfigMap + Secret で注入) |
| `bff/` | 4 BFF 共通の JWT 検証設定(JWT_ISSUER / JWT_JWKS_URL を ConfigMap で配布)+ Deployment env 注入 snippet |

### 適用順

1. `infra/cognito/README.md` の Step 1〜5 を Cognito 側で完了
2. `identity-broker/federation-configmap.yaml` の `XXXXXXXXX` を実際の User Pool ID に置換 → `kubectl apply`
3. `bff/jwt-configmap.yaml` を `kubectl apply`(各 BFF Deployment が `envFrom` で参照)
4. 各 BFF Deployment manifest(`bff/deployment-snippet.yaml` を雛形に各業態 BFF 分作成)を apply
5. web app の Vite ビルド時 env(`VITE_OIDC_*`)は CI から ImageBuilder に注入(本ディレクトリ管轄外)

### 既知の制約

- 本 manifest は MVP 用。 `kustomize` overlay で env 別(prod / staging / dev)分離は別タスク
- Secret は kubectl 経由 plain text。 prod は External Secrets Operator + AWS Secrets Manager 連携を前提
- Service / Ingress / NetworkPolicy / HPA は本 commit の対象外
