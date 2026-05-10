environment        = "staging"
vpc_cidr           = "10.1.0.0/16"
single_nat_gateway = true # cost 削減 (staging は短時間負荷試験のみ HA 不要)
