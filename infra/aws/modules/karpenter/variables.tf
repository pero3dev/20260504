variable "cluster_name" {
  description = "EKS cluster 名 (例: dev-platform)。 Karpenter sub-module が IAM role / SQS / Event rules の命名 prefix に使う。"
  type        = string
}

variable "oidc_provider_arn" {
  description = "EKS の OIDC provider ARN (eks stack の oidc_provider_arn output)。 Controller IRSA の trust policy に使う。"
  type        = string
}

variable "namespace" {
  description = "Karpenter Controller を deploy する K8s namespace。 Helm chart default に合わせ karpenter。"
  type        = string
  default     = "karpenter"
}

variable "service_account_name" {
  description = "Karpenter Controller の K8s ServiceAccount 名。 Helm chart default に合わせ karpenter。"
  type        = string
  default     = "karpenter"
}

variable "node_iam_role_additional_policies" {
  description = <<-EOT
    Karpenter node role に追加 attach する IAM policy ARN map (key = label、 value = ARN)。
    例: { ssm = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore" }
    default で標準 4 policy (Worker / ECR / SSM / CNI) は attach 済。 application 固有の権限を追加する場合に渡す。
  EOT
  type        = map(string)
  default     = {}
}

variable "tags" {
  description = "追加 tag。 stack 側 default_tags と merge される。"
  type        = map(string)
  default     = {}
}
