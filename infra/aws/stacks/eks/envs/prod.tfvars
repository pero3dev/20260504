environment = "prod"

# system NG は CoreDNS / Karpenter / ALB Controller / ESO / DD agent / ArgoCD 等を host する。
# application は Karpenter で別 NG (Phase B) で管理されるので、 system NG は容量小さくて十分。
# 各 AZ に 1 instance ずつ配置するため min/desired = 2 (multi-AZ)。
system_node_min_size       = 2
system_node_max_size       = 4
system_node_desired_size   = 2
system_node_instance_types = ["m7g.large"]
system_node_capacity_type  = "ON_DEMAND"
