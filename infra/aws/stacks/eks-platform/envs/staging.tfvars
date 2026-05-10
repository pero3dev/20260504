environment = "staging"

# staging は load test / E2E が走る想定。 cluster 全体で 50 vCPU / 200 GiB。
nodepool_default_cpu_limit    = "50"
nodepool_default_memory_limit = "200Gi"
