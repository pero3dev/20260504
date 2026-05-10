output "bucket_id" {
  description = "audit S3 バケット名 (= bucket_name)。 audit-service env var PLATFORM_AUDIT_ARCHIVE_BUCKET に渡す。"
  value       = module.bucket.bucket_id
}

output "bucket_arn" {
  description = "audit S3 バケットの ARN。 IRSA role の IAM policy に `Resource` として渡す。"
  value       = module.bucket.bucket_arn
}

output "glue_database_name" {
  description = "Glue Catalog database 名 (例: audit_db_prod)。 Athena query で `FROM audit_db_prod.audit_records` の形で使う。"
  value       = aws_glue_catalog_database.audit.name
}

output "glue_records_table_name" {
  description = "audit_records table 名。"
  value       = aws_glue_catalog_table.audit_records.name
}

output "glue_anchors_table_name" {
  description = "audit_anchors table 名。"
  value       = aws_glue_catalog_table.audit_anchors.name
}
