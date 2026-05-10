environment        = "prod"
vpc_cidr           = "10.2.0.0/16"
single_nat_gateway = false # 各 AZ に NAT GW を 1 つずつ (HA、 1 AZ 障害で他 AZ が継続稼働)
