environment = "prod"

# hotpath は 11.6k TPS 想定で memory-optimized + writer + reader 2 (合計 3、 各 AZ 1 個)。
# business / common は通常書込み traffic のため writer + reader 1 で十分。
hotpath_instance_class = "db.r7g.xlarge"
hotpath_instance_count = 3

business_instance_class = "db.r7g.large"
business_instance_count = 2

common_instance_class = "db.r7g.large"
common_instance_count = 2

deletion_protection          = true
skip_final_snapshot          = false
backup_retention_period      = 35
performance_insights_enabled = true
