# aurora stack — env ごとに 3 Aurora cluster を作る (ADR-0005 で確定したトポロジ)。
#
# cluster 構成:
#   hotpath  : inventory-core + master-data         (Bridge model: schema per tenant)
#   business : retail-ec + wholesale + tpl + manufacturing  (Bridge model)
#   common   : identity-broker + audit-service + notification + workflow + analytics + integration-hub
#              (Pool model: tenant_id column + RLS)
#
# 各 cluster の master password は AWS RDS managed (manage_master_user_password = true) で
# Secrets Manager に自動保管 + 自動 rotation。 master credential は admin 操作 (DB 作成 /
# user 作成 / GRANT) のみで使い、 services は per-service user を Flyway K8s Job で作成して使う。
#
# per-service の database / user / schema は本 stack 範囲外。 cluster 作成後の Flyway K8s Job
# で対応 (CLAUDE.md / ADR-0024)。
#
# 共有 client SG: aurora_client SG を 1 つ作って全 3 cluster の ingress に追加。 EKS node group
# が attach することで node 上の services が全 cluster に到達可能 (cluster 越え制御は IAM では
# なく per-service の DB credential で行う前提、 v1 simplification)。

# ----------------------------------------------------------------------------
# Remote state: vpc + kms
# ----------------------------------------------------------------------------

data "terraform_remote_state" "vpc" {
  backend = "s3"

  config = {
    bucket = "inventory-platform-tfstate"
    key    = "aws/stacks/vpc/${var.environment}.tfstate"
    region = "ap-northeast-1"
  }
}

data "terraform_remote_state" "kms" {
  backend = "s3"

  config = {
    bucket = "inventory-platform-tfstate"
    key    = "aws/stacks/kms/${var.environment}.tfstate"
    region = "ap-northeast-1"
  }
}

# ----------------------------------------------------------------------------
# 共有 aurora_client SG (全 cluster の ingress source、 EKS node が attach)
# ----------------------------------------------------------------------------

resource "aws_security_group" "aurora_client" {
  name_prefix = "${var.environment}-aurora-client-"
  description = "Attach to EKS node group / pod that needs Aurora access (${var.environment})"
  vpc_id      = data.terraform_remote_state.vpc.outputs.vpc_id

  # 空 SG (egress 制御は attach 側で実施)。 「この SG が attach されている = Aurora に話しかけてよい」 マーカ。
  lifecycle {
    create_before_destroy = true
  }
}

# ----------------------------------------------------------------------------
# 3 Aurora clusters
# ----------------------------------------------------------------------------

module "hotpath" {
  source = "../../modules/aurora-cluster"

  cluster_name             = "${var.environment}-aurora-hotpath"
  engine_version           = var.engine_version
  instance_class           = var.hotpath_instance_class
  instance_count           = var.hotpath_instance_count
  vpc_id                   = data.terraform_remote_state.vpc.outputs.vpc_id
  db_subnet_group_name     = data.terraform_remote_state.vpc.outputs.database_subnet_group_name
  kms_key_arn              = data.terraform_remote_state.kms.outputs.aurora_key_arn
  client_security_group_id = aws_security_group.aurora_client.id

  initial_database_name        = "platform_hotpath"
  deletion_protection          = var.deletion_protection
  skip_final_snapshot          = var.skip_final_snapshot
  backup_retention_period      = var.backup_retention_period
  performance_insights_enabled = var.performance_insights_enabled

  tags = {
    ClusterRole = "hotpath"
    Services    = "inventory-core,master-data"
  }
}

module "business" {
  source = "../../modules/aurora-cluster"

  cluster_name             = "${var.environment}-aurora-business"
  engine_version           = var.engine_version
  instance_class           = var.business_instance_class
  instance_count           = var.business_instance_count
  vpc_id                   = data.terraform_remote_state.vpc.outputs.vpc_id
  db_subnet_group_name     = data.terraform_remote_state.vpc.outputs.database_subnet_group_name
  kms_key_arn              = data.terraform_remote_state.kms.outputs.aurora_key_arn
  client_security_group_id = aws_security_group.aurora_client.id

  initial_database_name        = "platform_business"
  deletion_protection          = var.deletion_protection
  skip_final_snapshot          = var.skip_final_snapshot
  backup_retention_period      = var.backup_retention_period
  performance_insights_enabled = var.performance_insights_enabled

  tags = {
    ClusterRole = "business"
    Services    = "retail-ec,wholesale,tpl,manufacturing"
  }
}

module "common" {
  source = "../../modules/aurora-cluster"

  cluster_name             = "${var.environment}-aurora-common"
  engine_version           = var.engine_version
  instance_class           = var.common_instance_class
  instance_count           = var.common_instance_count
  vpc_id                   = data.terraform_remote_state.vpc.outputs.vpc_id
  db_subnet_group_name     = data.terraform_remote_state.vpc.outputs.database_subnet_group_name
  kms_key_arn              = data.terraform_remote_state.kms.outputs.aurora_key_arn
  client_security_group_id = aws_security_group.aurora_client.id

  initial_database_name        = "platform_common"
  deletion_protection          = var.deletion_protection
  skip_final_snapshot          = var.skip_final_snapshot
  backup_retention_period      = var.backup_retention_period
  performance_insights_enabled = var.performance_insights_enabled

  tags = {
    ClusterRole = "common"
    Services    = "identity-broker,audit-service,notification,workflow,analytics,integration-hub"
  }
}
