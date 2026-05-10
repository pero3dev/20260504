variable "environment" {
  description = "dev / staging / prod。 リソース名 prefix と Environment tag に注入。"
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment は dev / staging / prod のいずれか。"
  }
}

variable "region" {
  description = "AWS リージョン。 ADR の primary region は ap-northeast-1 (東京)。"
  type        = string
  default     = "ap-northeast-1"
}

variable "vpc_cidr" {
  description = <<-EOT
    VPC CIDR。 env ごとに重複しない /16 を割り当てる。
    本 stack の慣習: dev=10.0.0.0/16, staging=10.1.0.0/16, prod=10.2.0.0/16。
    将来の VPC peering / Transit Gateway 接続を見越して overlap 不可。
  EOT
  type        = string
}

variable "az_count" {
  description = "VPC を配置する AZ 数。 architecture.md の方針 (3 AZ 必須) に合わせて default 3。"
  type        = number
  default     = 3

  validation {
    condition     = var.az_count >= 2 && var.az_count <= 3
    error_message = "az_count は 2〜3 (Multi-AZ 必須、 ap-northeast-1 で利用可能 AZ は最大 3)。"
  }
}

variable "single_nat_gateway" {
  description = <<-EOT
    true なら全 AZ が 1 つの NAT GW を共有 (cost 削減、 dev/staging 用)。
    false なら each AZ に 1 つずつ NAT GW (HA、 prod 用)。
  EOT
  type        = bool
  default     = false
}

variable "enable_s3_gateway_endpoint" {
  description = "S3 gateway endpoint 作成 (free)。 audit S3 アクセスが NAT 経由しなくなる。"
  type        = bool
  default     = true
}

variable "enable_dynamodb_gateway_endpoint" {
  description = "DynamoDB gateway endpoint 作成 (free)。 terraform state lock アクセスが NAT 経由しなくなる。"
  type        = bool
  default     = true
}
