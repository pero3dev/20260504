variable "cluster_name" {
  description = "EKS cluster 名 (例: dev-platform)。 Karpenter discovery tag value にも使用。"
  type        = string
}

variable "kubernetes_version" {
  description = "Kubernetes version (例: 1.31)。 EKS の支援 version を指定。"
  type        = string
  default     = "1.31"
}

variable "vpc_id" {
  description = "VPC ID (vpc stack output)。"
  type        = string
}

variable "subnet_ids" {
  description = "node group / pod 配置用 subnet IDs (private compute subnets を渡す想定)。"
  type        = list(string)
}

variable "control_plane_subnet_ids" {
  description = "control plane ENI 配置用 subnet IDs。 通常は subnet_ids と同じ private subnets を渡す。"
  type        = list(string)
}

variable "cluster_kms_key_arn" {
  description = "EKS secrets encryption に使う KMS CMK ARN (kms stack の secrets_key_arn)。"
  type        = string
}

variable "cluster_endpoint_public_access" {
  description = <<-EOT
    public endpoint 有効化。 v1 では全 env false (kubectl access は SSM Session Manager / bastion 経由)。
    dev で operator 利便性のため true にする場合は cluster_endpoint_public_access_cidrs で IP 制限する。
  EOT
  type        = bool
  default     = false
}

variable "cluster_endpoint_public_access_cidrs" {
  description = "public endpoint の許可 CIDR list。 public_access = true の場合のみ有効。"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "system_node_min_size" {
  description = "system node group min size。 CoreDNS / Karpenter / ALB Controller 等を host する。"
  type        = number
  default     = 1
}

variable "system_node_max_size" {
  description = "system node group max size。"
  type        = number
  default     = 3
}

variable "system_node_desired_size" {
  description = "system node group desired size。 通常運用での目安、 actual は cluster autoscaler / Karpenter で動的調整。"
  type        = number
  default     = 2
}

variable "system_node_instance_types" {
  description = "system node group instance types (list)。 ARM64 (Graviton) 想定。"
  type        = list(string)
  default     = ["t4g.medium"]
}

variable "system_node_capacity_type" {
  description = "ON_DEMAND または SPOT。 system NG は安定性重視で ON_DEMAND default。"
  type        = string
  default     = "ON_DEMAND"
}

variable "tags" {
  description = "追加 tag。 stack 側 default_tags と merge される。"
  type        = map(string)
  default     = {}
}
