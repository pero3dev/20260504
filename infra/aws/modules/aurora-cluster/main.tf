# aurora-cluster wrapper — terraform-aws-modules/rds-aurora/aws (~> 9.0) を薄ラップ。
#
# プロジェクト共通の defaults を bind:
#   - engine = aurora-postgresql 16.x
#   - storage_encrypted = true、 KMS CMK 必須
#   - manage_master_user_password = true (AWS RDS managed master password、 自動 Secrets Manager + rotation)
#   - aurora_client_sg からの ingress 5432 のみ許可 (= EKS pod が aurora_client_sg を attach する形)
#   - apply_immediately = false (障害誘発を避け maintenance window で apply)
#   - deletion_protection / final snapshot は env で切替可
#
# 個別 service 専用 DB の作成は本 module 範囲外。 cluster 作成後に Flyway K8s Job で
# CREATE DATABASE / CREATE USER / GRANT の SQL を流す設計 (CLAUDE.md / ADR-0024)。

module "aurora" {
  source  = "terraform-aws-modules/rds-aurora/aws"
  version = "~> 9.0"

  name              = var.cluster_name
  engine            = "aurora-postgresql"
  engine_mode       = "provisioned"
  engine_version    = var.engine_version
  database_name     = var.initial_database_name
  master_username   = var.master_username
  port              = 5432
  storage_encrypted = true
  kms_key_id        = var.kms_key_arn

  # AWS RDS managed master password。 Secrets Manager に自動保管 + rotation。
  # services 側は AWS SDK で Secrets Manager から master credential を取得 (admin 操作のみ)、
  # 通常運用は per-service 用の DB user を Flyway Job で作成して使う設計。
  manage_master_user_password = true

  # network
  vpc_id               = var.vpc_id
  db_subnet_group_name = var.db_subnet_group_name

  # 上流 module v9 で SG ingress は security_group_rules map に書く。 client SG からの
  # 5432 ingress を 1 つだけ許可する rule を渡す。 from_port/to_port/protocol は engine port から推論。
  create_security_group = true
  security_group_name   = "${var.cluster_name}-cluster"
  security_group_rules = {
    client_sg_ingress = {
      source_security_group_id = var.client_security_group_id
      description              = "Aurora 5432 from aurora_client SG only"
    }
  }

  # instance 構成: writer (instance_count=1) または writer + readers (count >= 2)。
  # reader-1 にだけ promotion_tier=0 を渡し、 fail-over 時に最初に promote される。
  instances = {
    for i in range(var.instance_count) : "${i + 1}" => {
      instance_class      = var.instance_class
      promotion_tier      = i == 1 ? 0 : 1 # writer は default tier、 reader-1 を fail-over 候補に
      publicly_accessible = false
    }
  }

  # backup
  backup_retention_period      = var.backup_retention_period
  preferred_backup_window      = var.preferred_backup_window
  preferred_maintenance_window = var.preferred_maintenance_window

  # observability
  performance_insights_enabled          = var.performance_insights_enabled
  performance_insights_retention_period = var.performance_insights_enabled ? 7 : null

  enabled_cloudwatch_logs_exports = ["postgresql"]

  # safety
  apply_immediately   = false
  deletion_protection = var.deletion_protection
  skip_final_snapshot = var.skip_final_snapshot

  # `final_snapshot_identifier` は skip_final_snapshot=false の時に必須。 cluster 名 + timestamp で命名。
  final_snapshot_identifier = "${var.cluster_name}-final-snapshot"

  # tags
  tags = var.tags
}
