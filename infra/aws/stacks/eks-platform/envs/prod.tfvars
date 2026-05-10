environment = "prod"

# prod は 13 services + BFF/Web + 平時 + 11.6k TPS peak を想定して 1000 vCPU / 4 TiB。
# Karpenter は実 demand に応じて起動するので上限であり、 通常運用では遥かに低い。
# 本上限を超える pending pod が出たら Datadog alert (Karpenter "underutilized" / "scheduling")
# で検知する。
nodepool_default_cpu_limit    = "1000"
nodepool_default_memory_limit = "4000Gi"
