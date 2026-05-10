# elasticache stack — env ごとに ElastiCache for Redis replication group を 1 つ作る。
#
# 利用先:
#   - inventory-read-model: CQRS 読み側の Redis 投影 (architecture.md A2)
#   - identity-broker:      ADR-0023 即時 token revocation 用の revocation set/get
#
# 構成:
#   - Redis 7.1 cluster mode disabled (1 shard、 N nodes = 1 primary + N-1 replicas)
#   - TLS in-transit 必須、 AWS-managed key で at-rest 暗号化 (CMK 化は post-v1)
#   - auth token は random_password で生成、 Secrets Manager に <env>-secrets KMS で暗号化保管
#   - 2 つの SecurityGroup:
#       redis_cluster_sg : Redis ノード自身。 ingress = 6379 from redis_client_sg only
#       redis_client_sg  : 「Redis に接続する」 識別 SG。 EKS node group / pod が attach する
#
# 共有戦略 (v1): inventory-read-model + identity-broker が同 cluster を logical key prefix で
# 分離して使う (例: "rm:" / "rev:")。 運用分離が必要になったら post-v1 で 2 cluster に分割。

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
# Subnet group (database subnets from vpc stack)
# ----------------------------------------------------------------------------

resource "aws_elasticache_subnet_group" "this" {
  name        = "${var.environment}-platform-redis-subnets"
  description = "Redis subnet group (database subnets) for ${var.environment}"
  subnet_ids  = data.terraform_remote_state.vpc.outputs.database_subnet_ids
}

# ----------------------------------------------------------------------------
# Parameter group
# ----------------------------------------------------------------------------

resource "aws_elasticache_parameter_group" "this" {
  name        = "${var.environment}-platform-redis-params"
  family      = var.parameter_group_family
  description = "Redis parameter group for ${var.environment}"

  # eviction policy: read-model + revocation 双方とも「key 期限管理」 が前提なので
  # noeviction は危険 (memory 満杯で write 失敗)。 allkeys-lru (LRU で全 key から evict) を選択。
  parameter {
    name  = "maxmemory-policy"
    value = "allkeys-lru"
  }
}

# ----------------------------------------------------------------------------
# Security groups
# ----------------------------------------------------------------------------

resource "aws_security_group" "redis_client" {
  name_prefix = "${var.environment}-platform-redis-client-"
  description = "Attach to EKS node group / pod that needs Redis access (${var.environment})"
  vpc_id      = data.terraform_remote_state.vpc.outputs.vpc_id

  # 空 SG (egress も明示しない、 attach した側の egress は自前 SG で制御する)。
  # 「この SG が attach されている = Redis に話しかけてよい」 のマーカ。
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "redis_cluster" {
  name_prefix = "${var.environment}-platform-redis-cluster-"
  description = "Redis cluster SG (${var.environment}). Ingress only from redis_client SG."
  vpc_id      = data.terraform_remote_state.vpc.outputs.vpc_id

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group_rule" "redis_cluster_ingress" {
  type                     = "ingress"
  from_port                = 6379
  to_port                  = 6379
  protocol                 = "tcp"
  security_group_id        = aws_security_group.redis_cluster.id
  source_security_group_id = aws_security_group.redis_client.id
  description              = "Redis port from redis_client SG"
}

# ----------------------------------------------------------------------------
# Auth token — random_password + Secrets Manager
# ----------------------------------------------------------------------------

resource "random_password" "auth_token" {
  length  = 64
  special = true
  # ElastiCache auth token は alphanumeric + 一部記号のみ許可。 % ` & を除外。
  override_special = "!#$()*+-.:<=>?@[]^_{|}~"
}

resource "aws_secretsmanager_secret" "redis_auth" {
  name        = "${var.environment}/platform/redis/auth-token"
  description = "ElastiCache for Redis AUTH token (${var.environment}). Used by inventory-read-model + identity-broker."
  kms_key_id  = data.terraform_remote_state.kms.outputs.secrets_key_arn

  recovery_window_in_days = 7

  # AUTH token は cluster apply 前に存在する必要がある。 削除されると services 全滅。
  # ただし lifecycle prevent_destroy は terraform apply で都度の rotation を阻害するので
  # 設定しない。 削除 protection は recovery_window で代替 (削除予約後 7 日以内なら復元可)。
}

resource "aws_secretsmanager_secret_version" "redis_auth" {
  secret_id = aws_secretsmanager_secret.redis_auth.id

  secret_string = jsonencode({
    auth_token = random_password.auth_token.result
  })
}

# ----------------------------------------------------------------------------
# Replication group
# ----------------------------------------------------------------------------

resource "aws_elasticache_replication_group" "this" {
  replication_group_id = "${var.environment}-platform-redis"
  description          = "Inventory Platform Redis cluster (${var.environment}). Used by inventory-read-model + identity-broker."

  engine         = "redis"
  engine_version = var.engine_version
  node_type      = var.node_type
  port           = 6379

  # cluster mode disabled, 1 shard, N nodes
  num_cache_clusters         = var.num_cache_clusters
  automatic_failover_enabled = var.automatic_failover_enabled
  multi_az_enabled           = var.multi_az_enabled

  parameter_group_name = aws_elasticache_parameter_group.this.name
  subnet_group_name    = aws_elasticache_subnet_group.this.name
  security_group_ids   = [aws_security_group.redis_cluster.id]

  # 暗号化
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = random_password.auth_token.result
  # NOTE: at-rest CMK は v1 では AWS-managed (kms_key_id 未指定)。 post-v1 で
  # alias/<env>-redis を kms stack に追加して指定する。

  # snapshot
  snapshot_retention_limit = var.snapshot_retention_limit
  snapshot_window          = var.snapshot_window

  # maintenance
  maintenance_window = var.preferred_maintenance_window

  # auto upgrade
  auto_minor_version_upgrade = true

  # apply_immediately = true は本番障害誘発の温床なので明示的に false (default)。
  # parameter group 等の変更は次回 maintenance window で apply される。
  apply_immediately = false

  lifecycle {
    # auth_token は random_password を使っているため、 terraform apply 時に
    # ignore_changes しないと毎回 auth_token rotation を試みる。 rotation 自体は
    # 別途意図的に行う (random_password を `keepers` 等で trigger) 前提で ignore する。
    ignore_changes = [auth_token]
  }
}
