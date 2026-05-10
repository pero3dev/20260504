# glue-schema-registry stack — env ごとに 1 つ Glue Schema Registry を作る。
#
# 本 stack では registry の container だけ作成し、 個別 schema (Avro definition) は
# 後続 PR で services が JSON → Avro 移行するタイミングに同 stack に追加する設計。
#
# 運用 convention (本 stack README 参照):
#   - schema 名 = Kafka topic 名 (例: "inventory.movement.v1")
#   - data_format = AVRO (default)
#   - compatibility = BACKWARD_TRANSITIVE (architecture.md B2-2)
#   - schemas/<topic-name>.avsc に Avro JSON で定義、 file() で読み込み
#
# v1 時点では schemas/ は空。 services は引き続き JSON ObjectMapper 直送で動く。
# Avro 移行は post-v1 の段階的 PR で進める。

locals {
  registry_name = var.registry_name_override != "" ? var.registry_name_override : "${var.environment}-platform-schemas"
}

resource "aws_glue_registry" "this" {
  registry_name = local.registry_name
  description   = "Inventory Platform Kafka schemas registry (${var.environment}). compatibility = BACKWARD_TRANSITIVE, format = AVRO."
}

# 個別 schema は後続 PR で aws_glue_schema を追加する形で増やす。
# 例 (post-v1 でこの形を量産する想定):
#
# resource "aws_glue_schema" "inventory_movement_v1" {
#   schema_name       = "inventory.movement.v1"
#   registry_arn      = aws_glue_registry.this.arn
#   data_format       = "AVRO"
#   compatibility     = "BACKWARD_TRANSITIVE"
#   schema_definition = file("${path.module}/schemas/inventory.movement.v1.avsc")
# }
