# vpc stack — env ごとに 1 つ VPC を作る (env 依存 stack 第 1 号)。
#
# 対象リソース: VPC + 3 AZ × (public + private + database) subnets + NAT GW +
# Internet GW + route tables + S3/DynamoDB gateway endpoints。
#
# state は env ごとに分離 (`aws/stacks/vpc/<env>.tfstate`)。 後続 stack
# (eks / aurora / msk / elasticache) が `terraform_remote_state` で本 stack の
# outputs を参照する。
#
# CIDR 設計:
#   public   = vpc_cidr の /24 × 3 (各 AZ に 1 つ、 ALB 配置用)
#   private  = vpc_cidr の /20 × 3 (各 AZ に 1 つ、 EKS node groups 用)
#   database = vpc_cidr の /24 × 3 (各 AZ に 1 つ、 Aurora / MSK / Redis 用)

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  # primary region で利用可能な AZ から先頭 N 個を選ぶ。 名前を hardcode しないことで
  # opt-in AZ (ap-northeast-1b 等) を持たない account でも動く。
  azs = slice(data.aws_availability_zones.available.names, 0, var.az_count)

  # /16 vpc_cidr から:
  #   /24 × 3 = public  (10.x.0.0/24,  10.x.1.0/24,  10.x.2.0/24)
  #   /20 × 3 = private (10.x.16.0/20, 10.x.32.0/20, 10.x.48.0/20)
  #   /24 × 3 = database (10.x.100.0/24, 10.x.101.0/24, 10.x.102.0/24)
  public_subnets   = [for i in range(var.az_count) : cidrsubnet(var.vpc_cidr, 8, i)]
  private_subnets  = [for i in range(var.az_count) : cidrsubnet(var.vpc_cidr, 4, i + 1)]
  database_subnets = [for i in range(var.az_count) : cidrsubnet(var.vpc_cidr, 8, i + 100)]
}

module "vpc" {
  source = "../../modules/vpc"

  environment      = var.environment
  vpc_cidr         = var.vpc_cidr
  azs              = local.azs
  public_subnets   = local.public_subnets
  private_subnets  = local.private_subnets
  database_subnets = local.database_subnets

  single_nat_gateway = var.single_nat_gateway

  enable_s3_gateway_endpoint       = var.enable_s3_gateway_endpoint
  enable_dynamodb_gateway_endpoint = var.enable_dynamodb_gateway_endpoint

  # default_tags が provider 経由で全リソースに注入されるので、 module 側 var.tags は空でよい。
  tags = {}
}
