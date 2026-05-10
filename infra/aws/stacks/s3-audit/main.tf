# s3-audit stack — env ごとに ADR-0008 A4 の audit-service S3 バケットを 1 つ + Glue Catalog DB + 2 tables を作る。
#
# 構成:
#   - module.bucket (modules/s3-audit-bucket): Object Lock Compliance + KMS + lifecycle + Deny policy
#   - aws_glue_catalog_database "audit_db"
#   - aws_glue_catalog_table     "audit_records": JSONL gzip + partition projection (tenant × date)
#   - aws_glue_catalog_table     "audit_anchors": JSON 単発 + 同 partition projection
#
# 依存: kms stack の audit-s3 key (terraform_remote_state 経由で取得)。
# 既存 `infra/audit-s3/` 手動 runbook (Step 1〜10) の自動化版。 SQL ファイルは reference として残置。

# ----------------------------------------------------------------------------
# Remote state: kms stack
# ----------------------------------------------------------------------------

data "terraform_remote_state" "kms" {
  backend = "s3"

  config = {
    bucket = "inventory-platform-tfstate"
    key    = "aws/stacks/kms/${var.environment}.tfstate"
    region = "ap-northeast-1"
  }
}

# ----------------------------------------------------------------------------
# S3 audit bucket (module)
# ----------------------------------------------------------------------------

locals {
  bucket_name = var.bucket_name_override != "" ? var.bucket_name_override : "inventory-platform-${var.environment}-audit"
}

module "bucket" {
  source = "../../modules/s3-audit-bucket"

  bucket_name                = local.bucket_name
  kms_key_arn                = data.terraform_remote_state.kms.outputs.audit_s3_key_arn
  object_lock_retention_days = var.object_lock_retention_days
  lifecycle_expiration_days  = var.object_lock_retention_days
}

# ----------------------------------------------------------------------------
# Glue Catalog: database + 2 tables (audit_records / audit_anchors)
# 既存 infra/audit-s3/glue/*.sql を terraform 化したもの。 partition projection で
# `ALTER TABLE ADD PARTITION` 不要。
# ----------------------------------------------------------------------------

resource "aws_glue_catalog_database" "audit" {
  name        = "audit_db_${var.environment}"
  description = "Audit service WORM archive (ADR-0008 A4) — ${var.environment}"
}

resource "aws_glue_catalog_table" "audit_records" {
  name          = "audit_records"
  database_name = aws_glue_catalog_database.audit.name
  description   = "Audit records (1 record = 1 監査イベント、 JSONL + gzip)"
  table_type    = "EXTERNAL_TABLE"

  parameters = {
    "EXTERNAL"                      = "TRUE"
    "has_encrypted_data"            = "true"
    "projection.enabled"            = "true"
    "projection.tenant.type"        = "injected"
    "projection.date.type"          = "date"
    "projection.date.range"         = var.athena_projection_date_range
    "projection.date.format"        = "yyyy-MM-dd"
    "projection.date.interval"      = "1"
    "projection.date.interval.unit" = "DAYS"
    "storage.location.template"     = "s3://${module.bucket.bucket_id}/audit-records/tenant=$${tenant}/date=$${date}/"
  }

  partition_keys {
    name = "tenant"
    type = "string"
  }
  partition_keys {
    name = "date"
    type = "string"
  }

  storage_descriptor {
    location      = "s3://${module.bucket.bucket_id}/audit-records/"
    input_format  = "org.apache.hadoop.mapred.TextInputFormat"
    output_format = "org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat"

    ser_de_info {
      name                  = "audit-records-jsonserde"
      serialization_library = "org.openx.data.jsonserde.JsonSerDe"
      parameters = {
        "ignore.malformed.json" = "true"
        "dots.in.keys"          = "false"
        "case.insensitive"      = "true"
      }
    }

    columns {
      name = "tenantid"
      type = "string"
    }
    columns {
      name = "sequence"
      type = "bigint"
    }
    columns {
      name = "eventid"
      type = "bigint"
    }
    columns {
      name = "action"
      type = "string"
    }
    columns {
      name = "targettype"
      type = "string"
    }
    columns {
      name = "targetid"
      type = "string"
    }
    columns {
      name = "operatoruserid"
      type = "string"
    }
    columns {
      name = "operatortenantid"
      type = "string"
    }
    columns {
      name = "outcome"
      type = "string"
    }
    columns {
      name = "errorcode"
      type = "string"
    }
    columns {
      name = "readonly"
      type = "boolean"
    }
    columns {
      name = "payloadjson"
      type = "string"
    }
    columns {
      name = "occurredat"
      type = "string"
    }
    columns {
      name = "prevhash"
      type = "string"
    }
    columns {
      name = "hash"
      type = "string"
    }
  }
}

resource "aws_glue_catalog_table" "audit_anchors" {
  name          = "audit_anchors"
  database_name = aws_glue_catalog_database.audit.name
  description   = "Daily Merkle anchors (1 file = 1 anchor、 JSON 単発)"
  table_type    = "EXTERNAL_TABLE"

  parameters = {
    "EXTERNAL"                      = "TRUE"
    "has_encrypted_data"            = "true"
    "projection.enabled"            = "true"
    "projection.tenant.type"        = "injected"
    "projection.date.type"          = "date"
    "projection.date.range"         = var.athena_projection_date_range
    "projection.date.format"        = "yyyy-MM-dd"
    "projection.date.interval"      = "1"
    "projection.date.interval.unit" = "DAYS"
    "storage.location.template"     = "s3://${module.bucket.bucket_id}/audit-anchors/tenant=$${tenant}/date=$${date}/"
  }

  partition_keys {
    name = "tenant"
    type = "string"
  }
  partition_keys {
    name = "date"
    type = "string"
  }

  storage_descriptor {
    location      = "s3://${module.bucket.bucket_id}/audit-anchors/"
    input_format  = "org.apache.hadoop.mapred.TextInputFormat"
    output_format = "org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat"

    ser_de_info {
      name                  = "audit-anchors-jsonserde"
      serialization_library = "org.openx.data.jsonserde.JsonSerDe"
      parameters = {
        "ignore.malformed.json" = "true"
        "case.insensitive"      = "true"
      }
    }

    columns {
      name = "tenantid"
      type = "string"
    }
    columns {
      name = "anchordate"
      type = "string"
    }
    columns {
      name = "roothash"
      type = "string"
    }
    columns {
      name = "recordcount"
      type = "bigint"
    }
    columns {
      name = "firstsequence"
      type = "bigint"
    }
    columns {
      name = "lastsequence"
      type = "bigint"
    }
    columns {
      name = "computedat"
      type = "string"
    }
  }
}
