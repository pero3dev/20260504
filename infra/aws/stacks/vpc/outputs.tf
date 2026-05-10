output "vpc_id" {
  description = "VPC ID。 後続 stack の SecurityGroup / Endpoint / Cluster placement で参照。"
  value       = module.vpc.vpc_id
}

output "vpc_cidr_block" {
  description = "VPC CIDR (例: 10.0.0.0/16)。 SecurityGroup ingress で「同一 VPC 全体」 を許可する際に使う。"
  value       = module.vpc.vpc_cidr_block
}

output "azs" {
  description = "VPC が配置された AZ 名のリスト (順序つき)。 後続 stack で同じ AZ 順を使うとき参照。"
  value       = module.vpc.azs
}

output "public_subnet_ids" {
  description = "Public subnet ID リスト。 ALB internet-facing が配置される。"
  value       = module.vpc.public_subnet_ids
}

output "private_subnet_ids" {
  description = "Private compute subnet ID リスト。 EKS node groups が配置される。"
  value       = module.vpc.private_subnet_ids
}

output "database_subnet_ids" {
  description = "Private database subnet ID リスト。 Aurora / MSK / Redis が配置される。"
  value       = module.vpc.database_subnet_ids
}

output "database_subnet_group_name" {
  description = "Aurora が利用する RDS DB Subnet Group 名。 aurora stack に渡す。"
  value       = module.vpc.database_subnet_group_name
}

output "private_route_table_ids" {
  description = "Private route table ID リスト。 後続で interface endpoint を足す際に参照。"
  value       = module.vpc.private_route_table_ids
}

output "database_route_table_ids" {
  description = "Database route table ID リスト。 後続で endpoint を足す際に参照。"
  value       = module.vpc.database_route_table_ids
}

output "nat_public_ips" {
  description = "NAT Gateway の Elastic IP リスト。 EDI 等の外部 partner に Allow-list IP として共有する。"
  value       = module.vpc.nat_public_ips
}
