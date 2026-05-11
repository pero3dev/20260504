variable "environment" {
  description = "dev / staging / prod。"
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment は dev / staging / prod のいずれか。"
  }
}

variable "region" {
  description = "AWS リージョン。"
  type        = string
  default     = "ap-northeast-1"
}

# ----------------------------------------------------------------------------
# Karpenter chart version
# ----------------------------------------------------------------------------

variable "external_secrets_chart_version" {
  description = <<-EOT
    External Secrets Operator (ESO) Helm chart version。 charts.external-secrets.io から取得。
    v0.10+ は v1beta1 SecretStore / ClusterSecretStore を提供。 upgrade は別 PR で本変数を bump。
  EOT
  type        = string
  default     = "0.10.7"
}

variable "aws_lb_controller_chart_version" {
  description = <<-EOT
    AWS Load Balancer Controller Helm chart version。 aws.github.io/eks-charts から取得。
    K8s 1.31 サポートは chart 1.10.x 以降。 upgrade は別 PR で本変数を bump。
  EOT
  type        = string
  default     = "1.10.0"
}

variable "karpenter_chart_version" {
  description = <<-EOT
    Karpenter Helm chart version。 v1.x 系 (CRD API group = karpenter.k8s.aws / karpenter.sh)
    を指定する前提。 v0.x との混在は禁止。 default は安定版に固定し、 upgrade は別 PR で
    `karpenter_chart_version` 変数値の bump で行う運用。
  EOT
  type        = string
  default     = "1.1.1"
}

# ----------------------------------------------------------------------------
# NodePool sizing (env 依存)
# ----------------------------------------------------------------------------

variable "nodepool_default_cpu_limit" {
  description = "default NodePool の CPU 上限 (vCPU 単位)。 cluster 全体の application 用 cpu 予算。"
  type        = string
}

variable "nodepool_default_memory_limit" {
  description = "default NodePool の memory 上限 (例: 1000Gi)。"
  type        = string
}

variable "nodepool_default_arch" {
  description = <<-EOT
    NodePool の architecture (arm64 / amd64)。 v1 では arm64 を default (Graviton 優先で cost 効率)、
    amd64 が必要な workload (legacy binary) は別 NodePool を後追加する想定。
  EOT
  type        = string
  default     = "arm64"

  validation {
    condition     = contains(["arm64", "amd64"], var.nodepool_default_arch)
    error_message = "nodepool_default_arch は arm64 / amd64 のいずれか。"
  }
}

variable "nodepool_default_capacity_types" {
  description = "capacity-type の許可リスト (on-demand / spot)。 Karpenter は spot を優先選択しつつ、 spot 不在時に on-demand fallback。"
  type        = list(string)
  default     = ["on-demand", "spot"]
}
