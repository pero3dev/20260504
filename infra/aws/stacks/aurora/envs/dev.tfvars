environment = "dev"

# 全 cluster 同 spec、 1 instance ずつで cost 削減。
hotpath_instance_class  = "db.t4g.medium"
hotpath_instance_count  = 1
business_instance_class = "db.t4g.medium"
business_instance_count = 1
common_instance_class   = "db.t4g.medium"
common_instance_count   = 1

deletion_protection          = false
skip_final_snapshot          = true
backup_retention_period      = 1
performance_insights_enabled = false
