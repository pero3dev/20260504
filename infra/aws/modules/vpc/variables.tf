variable "environment" {
  description = "dev / staging / prod。 リソース名 prefix と Environment tag に使う。"
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR。 env ごとに重複しない /16 を割り当てる (dev=10.0/16, staging=10.1/16, prod=10.2/16)。"
  type        = string
}

variable "azs" {
  description = "VPC を配置する AZ 名のリスト (例: [\"ap-northeast-1a\", \"ap-northeast-1c\", \"ap-northeast-1d\"])。"
  type        = list(string)
}

variable "public_subnets" {
  description = "Public subnet CIDR list (ALB / NAT 配置用)。 azs と同じ長さで対応する AZ 順に並べる。"
  type        = list(string)
}

variable "private_subnets" {
  description = "Private compute subnet CIDR list (EKS node groups 配置用)。"
  type        = list(string)
}

variable "database_subnets" {
  description = "Private database subnet CIDR list (Aurora / MSK / Redis 配置用、 NACL で外部発信を更に制限する想定)。"
  type        = list(string)
}

variable "single_nat_gateway" {
  description = <<-EOT
    true なら全 AZ が 1 つの NAT GW を共有 (cost 削減、 dev/staging 用)。
    false なら each AZ に 1 つずつ NAT GW を作成 (HA、 prod 用)。
  EOT
  type        = bool
  default     = false
}

variable "enable_s3_gateway_endpoint" {
  description = "S3 gateway endpoint を作るか。 free でデータ転送 cost 削減 + audit S3 アクセスが NAT を経由しなくなる。"
  type        = bool
  default     = true
}

variable "enable_dynamodb_gateway_endpoint" {
  description = "DynamoDB gateway endpoint を作るか。 free で terraform state lock アクセスが NAT を経由しなくなる。"
  type        = bool
  default     = true
}

variable "tags" {
  description = "全リソース共通の追加 tag。 stack 側 default_tags と merge される。"
  type        = map(string)
  default     = {}
}
