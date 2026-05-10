environment = "dev"

# dev は応用 cap 軽め (10 vCPU / 40 GiB) で十分。 over-provision を防ぐ。
nodepool_default_cpu_limit    = "10"
nodepool_default_memory_limit = "40Gi"
