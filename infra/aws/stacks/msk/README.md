# msk stack

ADR-0024 並列フェーズの最後の 1 stack。 architecture.md C4 で確定した Apache Kafka 経路を MSK Serverless で構築。

## 何を作るか

env あたり:

- **`aws_msk_serverless_cluster`** `<env>-platform-msk` (IAM auth 専用、 VPC private endpoint)
- **`aws_security_group` × 2**:
  - `msk_cluster_sg`: Kafka brokers 自身、 ingress 9098 from `msk_client_sg` only
  - `msk_client_sg`: 「MSK に接続する」 マーカ SG、 EKS node group が attach
- **`aws_iam_policy_document` (data)**: services の IRSA role に attach する template (Connect / Topic ReadData / WriteData / DescribeTopic / CreateTopic / Group 操作)。 実 attach は per-service IRSA stack で別途

## なぜ MSK Serverless か (Provisioned ではない判断根拠)

| 観点 | Serverless 採用根拠 |
|---|---|
| Throughput | 200 MB/s ingress / 400 MB/s egress 上限。 peak 11.6k TPS × 3 events × 1 KB ≒ 50 MB/s で 1/4 余裕 |
| ops | broker sizing / EBS / partition rebalancing 全て不要 (auto) |
| auth | IAM auth 専用 (architecture.md C4 と整合)、 SASL/SCRAM や mTLS は不要 |
| pricing | 50 MB/s 程度では provisioned m7g.large × 3 broker より安い |
| 制約 | tiered storage 自動、 Kafka config tuning 不可 (defaults のみ)、 Kafka tx 一部制限 — outbox pattern 採用済 (ADR-0008) で問題なし |

将来 200 MB/s を超える traffic が確実視されるなら provisioned に切替える ADR を別途出す。 v1 規模では serverless で十分。

## 範囲外 (本 stack で扱わないもの)

- **Topic 作成**: services が起動時に `KafkaAdminClient.createTopics` で auto-create する設定 (default `auto.create.topics.enable=true`)。 production では明示的な K8s Job (`kafka-topics.sh`) で作成する別 PR を予定
- **per-service IRSA role の作成 + policy attach**: 本 stack は `msk_client_iam_policy_json` を output するのみ。 実 attach は per-service IRSA stack で
- **Glue Schema Registry binding**: MSK 側の binding API は無い。 client (Spring Kafka producer/consumer) が AWSGlueSchemaRegistry serializer を使い、 IRSA に `glue:GetSchema` 等を別途付与する形

## 依存

- `vpc` stack: `vpc_id`, `database_subnet_ids`

`glue-schema-registry` stack は本 stack から直接参照しない (services 側 IRSA で連携)。

## Apply 手順

bootstrap + iam-baseline + vpc 完了後 (kms / glue-schema-registry は本 stack で直接参照しない):

```bash
cd infra/aws/stacks/msk

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
- `bootstrap_brokers_sasl_iam` → services の `spring.kafka.bootstrap-servers`
- `msk_client_iam_policy_json` → per-service IRSA stack で `aws_iam_policy` 作成 → role に attach
- `msk_client_security_group_id` → eks-platform stack の node group `additional_security_group_ids`

## services 側 wire (Spring Kafka + IAM SASL)

依存追加 (services の pom.xml):

```xml
<dependency>
  <groupId>software.amazon.msk</groupId>
  <artifactId>aws-msk-iam-auth</artifactId>
  <version>2.x</version>
</dependency>
```

Spring config:

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}    # = bootstrap_brokers_sasl_iam
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: AWS_MSK_IAM
      sasl.jaas.config: software.amazon.msk.auth.iam.IAMLoginModule required;
      sasl.client.callback.handler.class: software.amazon.msk.auth.iam.IAMClientCallbackHandler
      # Glue Schema Registry serializer (^37 stack)
      value.serializer: com.amazonaws.services.schemaregistry.serializers.avro.AWSKafkaAvroSerializer
      registry.name: ${KAFKA_SCHEMA_REGISTRY_NAME}    # = glue-schema-registry stack output
      avroRecordType: SPECIFIC_RECORD
      autoRegistrationSetting: false
      region: ap-northeast-1
```

## per-env 差別化

MSK Serverless は broker sizing / storage / config 引数を持たない (auto-scaling)。 per-env tfvars は environment 名のみ。 dev / staging / prod の差は VPC + SG (= per-env vpc stack output) のみ。

cost も serverless は payload-based なので env ごとの fixed cost 差は少ない。 dev は traffic 少ないため数 USD/月、 prod は traffic 量に比例して増える従量モデル。

## Topic 命名規約 (architecture.md A2)

services が auto-create / K8s Job で作成する topic の命名:

| Topic | Producer | Consumer |
|---|---|---|
| `inventory.movement.v1` | inventory-core | inventory-read-model, audit, retail-ec, ... |
| `master.product.v1` | master-data | inventory-core (sku registry), 他 |
| `master.location.v1` | master-data | (need 出てきたら) |
| `master.partner.v1` | master-data | wholesale 等 |
| `audit.log.v1` | 全 service (AOP) | audit-service |
| `retail.order.placed.v1` | retail-ec | inventory-core (Saga reserve) |
| `retail.order.shipped.v1` | retail-ec | inventory-core (Saga ship) |
| `retail.order.cancelled.v1` | retail-ec | inventory-core (release) |
| `wholesale.order.placed.v1` | wholesale | inventory-core |
| ... 業態 OUTBOUND / Cancel 系も同形 | | |
| `tpl.stock.movement.v1` | tpl | inventory-core |
| `manufacturing.work_order.released.v1` | manufacturing | inventory-core |
| `manufacturing.work_order.completed.v1` | manufacturing | inventory-core |
| `manufacturing.completion.failed.v1` | inventory-core | manufacturing (compensation) |
| `workflow.instance.completed.v1` | workflow | event-driven 各種 |

詳細は `docs/architecture` または service の Outbox publisher 参照。

## 既知の制約

- Topic を terraform 化していない (Confluent Kafka provider の認証経路が複雑、 v1 では auto-create で運用)。 production では K8s Job で明示作成する別 PR を予定
- MSK Serverless の broker URL は `data.aws_msk_bootstrap_brokers` 経由でしか取得できない (cluster resource に直接 expose されない API 設計)。 本 stack は data source で取得して output する pattern
- IAM policy document は output JSON のみ。 実 IAM policy resource 化 + role attach は per-service IRSA stack で行うため、 本 stack apply 直後の services 起動時には MSK 接続不可 (IRSA 完了まで待つ)
- Glue Schema Registry との連携は client 側のみ (terraform 経由の binding なし)。 IRSA で `glue:GetSchema` / `glue:GetSchemaVersion` 等を services の role に追加する別 PR が必要
