# VPC wrapper — terraform-aws-modules/vpc/aws を薄くラップして
# プロジェクト共通のデフォルト (命名 / EKS subnet tags / DNS ON) を bind する。
#
# stack 側からは本 module 経由でしか VPC を作らない (ADR-0024 の wrapper 方針)。
# 上流 module の major version 変更の衝撃を本ファイル 1 箇所で吸収する。

module "this" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.14"

  name = "${var.environment}-vpc"
  cidr = var.vpc_cidr

  azs              = var.azs
  public_subnets   = var.public_subnets
  private_subnets  = var.private_subnets
  database_subnets = var.database_subnets

  enable_nat_gateway     = true
  single_nat_gateway     = var.single_nat_gateway
  one_nat_gateway_per_az = !var.single_nat_gateway

  enable_dns_hostnames = true
  enable_dns_support   = true

  # database subnets 用の dedicated route table と subnet group を有効化。
  # Aurora が自前で subnet group を作るときに本 route table を参照する。
  create_database_subnet_group       = true
  create_database_subnet_route_table = true

  # AWS Load Balancer Controller が ALB を自動配置するための EKS subnet 識別 tag。
  # public = internet-facing ALB、 private = internal-facing ALB の自動 discovery に必要。
  public_subnet_tags = {
    "kubernetes.io/role/elb" = "1"
  }
  private_subnet_tags = {
    "kubernetes.io/role/internal-elb" = "1"
  }

  tags = var.tags
}

# ----------------------------------------------------------------------------
# Gateway endpoints (S3 / DynamoDB は無料、 NAT 経由トラフィックを大幅削減)
# ----------------------------------------------------------------------------

data "aws_region" "current" {}

resource "aws_vpc_endpoint" "s3" {
  count = var.enable_s3_gateway_endpoint ? 1 : 0

  vpc_id            = module.this.vpc_id
  service_name      = "com.amazonaws.${data.aws_region.current.name}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids = concat(
    module.this.private_route_table_ids,
    module.this.database_route_table_ids,
  )

  tags = merge(var.tags, {
    Name = "${var.environment}-s3-endpoint"
  })
}

resource "aws_vpc_endpoint" "dynamodb" {
  count = var.enable_dynamodb_gateway_endpoint ? 1 : 0

  vpc_id            = module.this.vpc_id
  service_name      = "com.amazonaws.${data.aws_region.current.name}.dynamodb"
  vpc_endpoint_type = "Gateway"
  route_table_ids = concat(
    module.this.private_route_table_ids,
    module.this.database_route_table_ids,
  )

  tags = merge(var.tags, {
    Name = "${var.environment}-dynamodb-endpoint"
  })
}
