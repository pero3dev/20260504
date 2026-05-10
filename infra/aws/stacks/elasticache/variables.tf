variable "environment" {
  description = "dev / staging / prod。 リソース名 prefix と Environment tag に注入。"
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment は dev / staging / prod のいずれか。"
  }
}

variable "region" {
  description = "AWS リージョン。"
  type        = string
  default     = "ap-northeast-1"
}

variable "node_type" {
  description = <<-EOT
    ElastiCache ノードタイプ。 env ごとに分離:
      dev     = cache.t4g.micro  (低 cost、 1 vCPU / 0.5 GiB)
      staging = cache.t4g.small  (1 vCPU / 1.37 GiB)
      prod    = cache.r7g.large  (memory-optimized、 2 vCPU / 13.07 GiB、 11.6k TPS 想定)
  EOT
  type        = string
}

variable "num_cache_clusters" {
  description = <<-EOT
    replication group 内の cache cluster 数 (= 1 primary + N-1 replicas)。
    最低 2 (1 primary + 1 replica)。 prod は 3 (1 primary + 2 replicas) 推奨。
  EOT
  type        = number

  validation {
    condition     = var.num_cache_clusters >= 2 && var.num_cache_clusters <= 6
    error_message = "num_cache_clusters は 2〜6 (Redis cluster mode disabled の上限)。"
  }
}

variable "automatic_failover_enabled" {
  description = "primary 障害時の自動 failover。 staging / prod = true、 dev = false (cost 削減)。"
  type        = bool
}

variable "multi_az_enabled" {
  description = <<-EOT
    cache cluster を複数 AZ に分散配置。 staging / prod = true。
    automatic_failover_enabled = true が前提 (AWS 制約)。
  EOT
  type        = bool
}

variable "snapshot_retention_limit" {
  description = <<-EOT
    自動 snapshot 保持日数。
      dev     = 0 (snapshot 無効、 cost 削減)
      staging = 1
      prod    = 7
  EOT
  type        = number
}

variable "engine_version" {
  description = "Redis engine version。 ElastiCache 提供の最新安定版を使う。"
  type        = string
  default     = "7.1"
}

variable "parameter_group_family" {
  description = "Parameter group の engine family。 engine_version と整合させる。"
  type        = string
  default     = "redis7"
}

variable "preferred_maintenance_window" {
  description = "maintenance window (UTC)。 default = 日曜 18:00-19:00 UTC (= JST 月曜 03:00-04:00、 低 traffic 帯)。"
  type        = string
  default     = "sun:18:00-sun:19:00"
}

variable "snapshot_window" {
  description = "snapshot window (UTC)。 default = 17:00-18:00 UTC (= JST 02:00-03:00、 maintenance の前)。"
  type        = string
  default     = "17:00-18:00"
}
