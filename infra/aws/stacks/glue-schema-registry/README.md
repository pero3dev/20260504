# glue-schema-registry stack

ADR-0024 並列フェーズの 1 つ。 architecture.md B2-2 で「採用」 と確定した AWS Glue Schema Registry の registry container を env ごとに 1 つ作る。

## 何を作るか

env あたり:

- **`aws_glue_registry`** `<env>-platform-schemas`
  - description: 「Inventory Platform Kafka schemas registry」 + compatibility / format note
  - 個別 schema は本 PR では作らず、 services の Avro 移行に合わせて段階的に追加

## 範囲外 (本 stack で作らないもの)

- 個別 `aws_glue_schema` 定義 (`inventory.movement.v1` 等の Avro schema): 後続 PR で services が JSON ObjectMapper → Avro Serializer に移行するタイミングに 1 schema 1 PR で追加
- MSK の topic 定義: `msk` stack の責務 (本 stack 完了後に依存して作成)
- IAM policy (services が registry に schema 登録 / 読み取り するための権限): IRSA stack で対応

## 運用 convention (重要)

本 stack に schema を追加する PR を出す際の規約:

| 項目 | 値 |
|---|---|
| schema 名 | Kafka topic 名と完全一致 (例: `inventory.movement.v1`) |
| `data_format` | `AVRO` (default、 architecture.md B2-2 で確定) |
| `compatibility` | `BACKWARD_TRANSITIVE` (architecture.md B2-2 + ADR-0019 で確定) |
| schema 定義 | `schemas/<topic-name>.avsc` に Avro JSON で配置、 `file()` で読み込み |
| 命名 namespace | `com.example.inventory.<context>.event` (例: `com.example.inventory.inventory.event`) |
| version 管理 | terraform で同 schema を update すると新 version が自動登録、 旧 version は registry 側で保持 (`BACKWARD_TRANSITIVE` で互換性 check) |

## Apply 手順

bootstrap + iam-baseline 完了後 (kms / vpc は本 stack の依存に無いので並列 OK):

```bash
cd infra/aws/stacks/glue-schema-registry

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
- `registry_name` → AWS Glue Schema Registry serializer の `registry.name` 設定に渡す
- `registry_arn` → IAM policy + msk stack で参照

## services 側の wire 方法 (post-v1 段階展開)

services が JSON 直送 → Glue SR + Avro に移行する場合の Spring 設定例:

```yaml
spring:
  kafka:
    producer:
      properties:
        value.serializer: com.amazonaws.services.schemaregistry.serializers.avro.AWSKafkaAvroSerializer
        registry.name: ${KAFKA_SCHEMA_REGISTRY_NAME}   # = terraform output registry_name
        avroRecordType: SPECIFIC_RECORD                 # 自動生成型を使うなら
        compatibility: BACKWARD_TRANSITIVE
        autoRegistrationSetting: false                  # 重要: terraform 管理に揃えるため auto register OFF
        region: ap-northeast-1
    consumer:
      properties:
        value.deserializer: com.amazonaws.services.schemaregistry.deserializers.avro.AWSKafkaAvroDeserializer
        registry.name: ${KAFKA_SCHEMA_REGISTRY_NAME}
        avroRecordType: SPECIFIC_RECORD
        region: ap-northeast-1
```

**`autoRegistrationSetting: false`** が重要。 これを `true` にすると services が起動時に schema を勝手に作成してしまい、 terraform 管理から外れる。 production では必ず `false` にし、 schema 追加は terraform PR 経由に統一する。

## schema 追加 PR の例

```hcl
resource "aws_glue_schema" "inventory_movement_v1" {
  schema_name       = "inventory.movement.v1"
  registry_arn      = aws_glue_registry.this.arn
  data_format       = "AVRO"
  compatibility     = "BACKWARD_TRANSITIVE"
  description       = "Inventory movement event (reserve / ship / receive / release)"
  schema_definition = file("${path.module}/schemas/inventory.movement.v1.avsc")
}
```

`schemas/inventory.movement.v1.avsc`:

```json
{
  "type": "record",
  "namespace": "com.example.inventory.inventory.event",
  "name": "InventoryMovementEvent",
  "fields": [
    {"name": "tenantId", "type": "string"},
    {"name": "skuId", "type": "long"},
    ...
  ]
}
```

## 既知の制約

- v1 では schemas/ ディレクトリは空 (.gitkeep のみ)。 services は引き続き JSON ObjectMapper 直送で動く
- Avro 自動生成型 (`avro-maven-plugin`) と service コード側の DTO の二重管理が発生するので、 services の Avro 移行 PR では Pact 契約 (consumer-driven) で互換性を担保する戦略 (ADR-0019)
- registry を削除すると配下 schema が全て消える。 `prevent_destroy` は本 PR では設定していない (registry 自体を作り直す PR が今後発生する可能性があるため)。 schema を運用開始した後の destroy は事故、 schema 追加 PR で `prevent_destroy = true` を `aws_glue_registry` に追加する
- BACKWARD_TRANSITIVE は「全ての過去 version と互換」 を要求し、 strict すぎる場合は `BACKWARD` (直前 version とのみ互換) に緩和できるが、 architecture.md で B2-2 として確定しているので変更は ADR で扱う
