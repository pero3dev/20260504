# elasticache stack

ADR-0024 並列フェーズの 1 つ。 architecture.md A2 / B4 で確定した ElastiCache for Redis を env ごとに 1 cluster 作る。

## 利用先

- **inventory-read-model**: CQRS 読み側の Redis 投影 (architecture.md A2 #4)
- **identity-broker**: ADR-0023 即時 token revocation の revocation set/get

v1 は 1 cluster を共有 (logical key prefix で分離: `rm:` / `rev:`)。 運用分離が必要なら post-v1 で 2 cluster 化を検討。

## 何を作るか

env あたり:

- **`aws_elasticache_replication_group`** `<env>-platform-redis`
  - Redis 7.1, cluster mode disabled (1 shard、 N nodes)
  - TLS in-transit + AWS-managed key で at-rest 暗号化
  - auth token 必須
- **`aws_elasticache_subnet_group`**: vpc stack の database subnets を使用
- **`aws_elasticache_parameter_group`** `<env>-platform-redis-params`
  - `maxmemory-policy = allkeys-lru` (eviction で memory 満杯を回避)
- **`aws_security_group` × 2**:
  - `redis_cluster`: Redis ノード自身、 ingress 6379 from `redis_client` SG only
  - `redis_client`: 「Redis に接続する」 マーカ SG、 EKS node group / pod が attach
- **`random_password` + `aws_secretsmanager_secret`**: auth token 64 chars、 kms stack の `<env>-secrets` で暗号化

## env ごとのサイジング

| env | node_type | num | auto_failover | multi_az | snapshot 日数 |
|---|---|---|---|---|---|
| dev | `cache.t4g.micro` | 2 | false | false | 0 |
| staging | `cache.t4g.small` | 2 | true | true | 1 |
| prod | `cache.r7g.large` | 3 | true | true | 7 |

`num_cache_clusters` は cluster mode disabled での総ノード数 (1 primary + N-1 replicas)。 prod は 3 ノードで「primary + 2 replica」 構成、 1 AZ 障害でも 2/3 残存。

## 依存

- `vpc` stack: `database_subnet_ids`, `vpc_id` (SG 配置)
- `kms` stack: `secrets_key_arn` (Secrets Manager の auth token 暗号化)

## Apply 手順

bootstrap + iam-baseline + vpc + kms 完了後:

```bash
cd infra/aws/stacks/elasticache

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

完了後:
- `primary_endpoint_address` → services の `spring.redis.host`
- `port` (= 6379) → `spring.redis.port`
- `auth_token_secret_arn` → External Secrets Operator で K8s Secret に sync、 `spring.redis.password` にマップ
- `redis_client_security_group_id` → eks-platform で EKS node group の追加 SG として attach (これにより node 上の全 pod が Redis に到達可)

## services 側の wire 例 (Spring Boot)

```yaml
spring:
  redis:
    host: ${REDIS_HOST}              # = primary_endpoint_address
    port: 6379
    password: ${REDIS_PASSWORD}      # = ESO から Secret 経由で注入された auth token
    ssl: true                        # TLS in-transit
    timeout: 1000
    lettuce:
      pool:
        max-active: 16
        max-idle: 8
        min-idle: 2
```

K8s 側 (External Secrets Operator):

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: redis-auth
  namespace: inventory-read-model
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secretsmanager
    kind: ClusterSecretStore
  target:
    name: redis-auth
  data:
    - secretKey: REDIS_PASSWORD
      remoteRef:
        key: <env>/platform/redis/auth-token
        property: auth_token
```

## SG attach pattern (重要)

**Redis に接続する側 (EKS node group / pod) は `redis_client_security_group_id` を追加 SG として attach する必要がある。**

eks-platform stack で node group の `additional_security_group_ids` に `redis_client_security_group_id` を追加する想定:

```hcl
module "eks_node_group" {
  ...
  additional_security_group_ids = [
    data.terraform_remote_state.elasticache.outputs.redis_client_security_group_id,
    # 他 client SG (msk_client / aurora_client 等) も同様に attach
  ]
}
```

これにより node 上で動く全 pod が Redis に到達可能になる (pod レベルの SG 制御は SG-for-Pods が必要、 v1 では node レベル制御で十分)。

## auth token rotation

`random_password` で生成した auth token は `aws_elasticache_replication_group` の `lifecycle.ignore_changes = [auth_token]` で apply 時に rotation を試みないよう制御。

意図的な rotation:
1. `random_password` を `keepers` 引数で trigger (rotation_id を変更等)
2. terraform apply で新 token 生成 + Secrets Manager 更新
3. 別途 ElastiCache 側 `aws elasticache modify-replication-group --auth-token-update-strategy ROTATE` を CLI/SDK で実行 (terraform は AUTH rotation API 直接 cover していない)
4. ESO refresh で services に新 token 配布

詳細手順は post-v1 で runbook 化する。

## 既知の制約

- v1 では at-rest 暗号化が AWS-managed key (CMK 化は post-v1)。 SecOps 要件確定後に `kms` stack に `<env>-redis` key を追加し、 本 stack に `kms_key_id` を渡す PR を出す
- cluster mode disabled (= 1 shard) なので horizontal scaling 不可。 11.6k TPS 想定だが scale-up (node_type 大型化) で対応、 shard 化は post-v1 (cluster mode enabled に切替えると endpoint API が変わるため services 改修必要)
- v1 は 1 cluster 共有。 inventory-read-model の load が identity-broker revocation 経路に影響する可能性あり (memory 圧迫時の eviction 等)。 Datadog memory monitor で先回り検知 + post-v1 で 2 cluster 分割
- snapshot は `<env>-platform-redis` の自動 snapshot のみ。 backup の S3 export は別 PR で検討
