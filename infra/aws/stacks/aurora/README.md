# aurora stack

ADR-0024 並列フェーズの 1 つ。 ADR-0005 で確定した 3 Aurora cluster トポロジを env ごとに作る。

## 何を作るか

env あたり 3 cluster + 1 共有 SG:

| Cluster | サービス | Multi-tenant model |
|---|---|---|
| `<env>-aurora-hotpath` | inventory-core, master-data | Bridge (schema per tenant) |
| `<env>-aurora-business` | retail-ec, wholesale, tpl, manufacturing | Bridge |
| `<env>-aurora-common` | identity-broker, audit-service, notification, workflow, analytics, integration-hub | Pool (tenant_id + RLS) |

各 cluster:
- Aurora PostgreSQL 16.4
- KMS CMK 暗号化 (kms stack の `<env>-aurora`)
- AWS RDS managed master password (Secrets Manager 自動保管 + rotation)
- placeholder DB `platform_<role>`、 per-service DB は post-deploy Flyway K8s Job で作成
- writer + readers (env ごと別 sizing)
- backup retention 1/7/35 日 (dev/staging/prod)
- Performance Insights 7 日保持 (staging/prod)
- CloudWatch postgresql logs export

共有 `aurora_client` SG: 全 cluster の ingress source。 EKS node group が attach することで全 cluster 到達可能 (cluster 越え制御は credential 側で行う前提、 v1 simplification)。

## per-env サイジング

| env | hotpath | business | common | 合計 instance |
|---|---|---|---|---|
| dev | t4g.medium × 1 | t4g.medium × 1 | t4g.medium × 1 | 3 |
| staging | t4g.medium × 2 | t4g.medium × 2 | t4g.medium × 2 | 6 |
| prod | r7g.xlarge × 3 | r7g.large × 2 | r7g.large × 2 | 7 |

prod の hotpath は 11.6k TPS 想定で memory-optimized + 3 instances (writer + AZ ごと reader)。 business / common は通常書込み traffic のため writer + reader 1 で十分。

## 依存

- `vpc` stack: `vpc_id`, `database_subnet_group_name`
- `kms` stack: `aurora_key_arn`

## Apply 手順

bootstrap + iam-baseline + vpc + kms 完了後:

```bash
cd infra/aws/stacks/aurora

# dev
terraform init -backend-config=envs/dev.backend.hcl
terraform plan -var-file=envs/dev.tfvars
terraform apply -var-file=envs/dev.tfvars

# staging
terraform init -reconfigure -backend-config=envs/staging.backend.hcl
terraform plan -var-file=envs/staging.tfvars
terraform apply -var-file=envs/staging.tfvars

# prod
terraform init -reconfigure -backend-config=envs/prod.backend.hcl
terraform plan -var-file=envs/prod.tfvars
terraform apply -var-file=envs/prod.tfvars
```

完了後 outputs から:
- `<role>_cluster_endpoint` → 各 service の `spring.datasource.url`
- `<role>_master_user_secret_arn` → admin が DB / user 作成 時に Secrets Manager から取得
- `aurora_client_security_group_id` → eks-platform stack の node group `additional_security_group_ids` に追加

## post-apply: per-service DB / user 作成 (別 PR で対応予定)

cluster 作成後、 各 service 専用の DB / user / schema を Flyway K8s Job で作成する想定。 admin 接続 で実行する SQL の例:

```sql
-- hotpath cluster の master 接続で実行
CREATE DATABASE inventory_core;
CREATE DATABASE master_data;

-- per-service user (各 service は自 DB のみアクセス、 ADR-0005 原則)
CREATE USER inventory_core_user WITH PASSWORD '<service password from Secrets Manager>';
GRANT CONNECT ON DATABASE inventory_core TO inventory_core_user;
GRANT ALL ON SCHEMA public TO inventory_core_user;

-- Bridge model (hotpath / business): tenant schema は service 起動時に動的に作成 + SET search_path
-- Pool model (common): RLS policy を service 側 Flyway migration で適用
```

詳細手順 + Flyway K8s Job manifest は別 PR で本 stack の README に追記予定。

## services 側の wire 例 (Spring Boot + MyBatis)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${AURORA_HOTPATH_ENDPOINT}:5432/inventory_core
    username: ${INVENTORY_CORE_DB_USER}                  # ESO 経由で Secrets Manager から
    password: ${INVENTORY_CORE_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 5000
      idle-timeout: 60000
      max-lifetime: 1800000
```

K8s 側 (External Secrets Operator):

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: inventory-core-db
  namespace: inventory-core
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secretsmanager
    kind: ClusterSecretStore
  target:
    name: inventory-core-db
  data:
    - secretKey: INVENTORY_CORE_DB_USER
      remoteRef:
        key: <env>/services/inventory-core/db
        property: username
    - secretKey: INVENTORY_CORE_DB_PASSWORD
      remoteRef:
        key: <env>/services/inventory-core/db
        property: password
```

## Multi-tenant 戦略 (ADR-0003)

- **Bridge** (hotpath / business): MyBatis tenant interceptor で `SET search_path TO tenant_<id>`、 schema 毎にデータ分離
- **Pool** (common): tenant_id 列 + Row-Level Security (RLS) policy、 connection 単位で `SET app.current_tenant = '<id>'`

詳細は CLAUDE.md + ADR-0003 + `inventory-commons/commons-tenant`。

## DR (architecture.md D2)

- 自動 backup: 1/7/35 日保持 (dev/staging/prod)
- PITR (point-in-time recovery): backup 保持期間内の任意時点に復旧可能
- 日次 snapshot は backup 経由で自動取得
- DR 演習 (architecture.md D2): 半年に 1 回、 別 region への snapshot 復旧テスト (post-v1 で別 stack の `aurora-dr` を作成して runbook 化予定)

## 既知の制約

- v1 では cluster 越えの IAM-based access control 不在 (= aurora_client_sg を attach した node 上の pod は技術的に全 cluster に到達可能)。 cluster 越え制御は credential 経由 (services は自 cluster の credential しか持たない)。 IAM database authentication 化は post-v1
- per-service DB / user は Flyway K8s Job で別途作成 (terraform 範囲外)。 cluster 作成直後の状態では placeholder DB のみで service が起動できない
- engine version major upgrade (16.x → 17.x 等) は in-place 不可、 別 cluster 立ち上げて application 切替 + 旧 destroy の手順
- prod r7g.xlarge × 3 + r7g.large × 4 = $3,500/month 程度の cost (on-demand)。 Reserved Instance 適用で 30〜50% 削減可、 production trafficパターン安定後に検討
