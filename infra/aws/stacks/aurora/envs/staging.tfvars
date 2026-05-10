environment = "staging"

# staging は writer + reader 1 個ずつで HA を確保しつつ cost も抑える。
hotpath_instance_class  = "db.t4g.medium"
hotpath_instance_count  = 2
business_instance_class = "db.t4g.medium"
business_instance_count = 2
common_instance_class   = "db.t4g.medium"
common_instance_count   = 2

deletion_protection          = true
skip_final_snapshot          = false
backup_retention_period      = 7
performance_insights_enabled = true
