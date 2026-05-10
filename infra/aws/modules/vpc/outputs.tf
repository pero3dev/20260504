output "vpc_id" {
  description = "VPC ID。 後続 stack (eks / aurora / msk / elasticache / 各種 endpoint) が参照する。"
  value       = module.this.vpc_id
}

output "vpc_cidr_block" {
  description = "VPC CIDR (例: 10.0.0.0/16)。 Security Group rule で peer 全体を許可する際に使う。"
  value       = module.this.vpc_cidr_block
}

output "azs" {
  description = "VPC が配置された AZ 名のリスト。 後続 stack で同じ AZ 順に subnet を引きたい時に参照。"
  value       = module.this.azs
}

output "public_subnet_ids" {
  description = "Public subnet ID リスト (ALB internet-facing 用)。"
  value       = module.this.public_subnets
}

output "private_subnet_ids" {
  description = "Private compute subnet ID リスト (EKS node groups 用)。"
  value       = module.this.private_subnets
}

output "database_subnet_ids" {
  description = "Private database subnet ID リスト (Aurora / MSK / Redis 用)。"
  value       = module.this.database_subnets
}

output "database_subnet_group_name" {
  description = "Aurora が利用する RDS DB Subnet Group 名 (本 module 内で作成済)。"
  value       = module.this.database_subnet_group_name
}

output "public_route_table_ids" {
  description = "Public route table ID リスト。"
  value       = module.this.public_route_table_ids
}

output "private_route_table_ids" {
  description = "Private route table ID リスト。 VPC endpoint を後追いで足すときに使う。"
  value       = module.this.private_route_table_ids
}

output "database_route_table_ids" {
  description = "Database route table ID リスト。 VPC endpoint を後追いで足すときに使う。"
  value       = module.this.database_route_table_ids
}

output "nat_public_ips" {
  description = "NAT Gateway の Elastic IP リスト。 外部 partner に Allow-list IP として共有する場合に使う。"
  value       = module.this.nat_public_ips
}
