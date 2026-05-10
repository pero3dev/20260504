variable "cluster_name" {
  description = "Aurora cluster 名 (例: prod-aurora-hotpath)。"
  type        = string
}

variable "engine_version" {
  description = "Aurora PostgreSQL engine version。 LTS 系統を採用 (16.x)。"
  type        = string
  default     = "16.4"
}

variable "instance_class" {
  description = "writer / reader 共通の instance class (例: db.t4g.medium、 db.r7g.xlarge)。"
  type        = string
}

variable "instance_count" {
  description = "writer + reader の総数 (1 = writer のみ、 2 = writer + 1 reader、 3 = writer + 2 reader)。"
  type        = number

  validation {
    condition     = var.instance_count >= 1 && var.instance_count <= 6
    error_message = "instance_count は 1〜6 (cluster 内 instance 上限)。"
  }
}

variable "vpc_id" {
  description = "VPC ID。 SG 配置に使う。"
  type        = string
}

variable "db_subnet_group_name" {
  description = "RDS DB Subnet Group 名 (vpc stack の database_subnet_group_name)。"
  type        = string
}

variable "kms_key_arn" {
  description = "at-rest 暗号化用 KMS CMK ARN (kms stack の aurora_key_arn)。"
  type        = string
}

variable "client_security_group_id" {
  description = "Aurora に接続する側の SG ID (aurora_client_sg)。 cluster SG の ingress source に使う。"
  type        = string
}

variable "master_username" {
  description = "Aurora master username。 default は postgres-style の `aurora_master`。"
  type        = string
  default     = "aurora_master"
}

variable "initial_database_name" {
  description = <<-EOT
    cluster 作成時に作る placeholder database 名 (例: `platform`)。
    各 service 専用 DB は post-deploy の Flyway K8s Job で別途作成する設計のため、
    本 module では initial 1 個のみ。
  EOT
  type        = string
  default     = "platform"
}

variable "deletion_protection" {
  description = "deletion protection。 prod = true 必須、 dev/staging も基本 true。"
  type        = bool
  default     = true
}

variable "skip_final_snapshot" {
  description = "destroy 時に final snapshot を skip するか。 dev = true、 staging/prod = false。"
  type        = bool
  default     = false
}

variable "backup_retention_period" {
  description = "自動 backup 保持日数 (1〜35)。 ADR-0024 + architecture.md D2 で prod 35 日。"
  type        = number
  default     = 35
}

variable "preferred_backup_window" {
  description = "backup window (UTC)。 default = 17:00-18:00 UTC (= JST 02:00-03:00、 低 traffic 帯)。"
  type        = string
  default     = "17:00-18:00"
}

variable "preferred_maintenance_window" {
  description = "maintenance window (UTC)。 default = 日曜 18:00-19:00 UTC (= JST 月曜 03:00-04:00)。"
  type        = string
  default     = "sun:18:00-sun:19:00"
}

variable "performance_insights_enabled" {
  description = "RDS Performance Insights 有効化。 staging/prod は true 推奨 (slow query 解析)。"
  type        = bool
  default     = true
}

variable "tags" {
  description = "追加 tag。 stack 側 default_tags と merge される。"
  type        = map(string)
  default     = {}
}
