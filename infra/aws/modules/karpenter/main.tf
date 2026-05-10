# karpenter wrapper — terraform-aws-modules/eks/aws//modules/karpenter (~> 20.0) を薄ラップ。
#
# Karpenter 動作に必要な AWS リソースのみ作成する (K8s リソースは別 stack):
#   - Controller IRSA role + canned policy (Karpenter v1.0+ permissions)
#   - Node IAM role (専用、 system NG とは別)
#   - Instance profile (Karpenter NodeClass が node 起動時に attach)
#   - SQS interruption queue (Spot 中断 / instance state change を受信)
#   - EventBridge rules + targets (上記 SQS への routing)
#
# Helm chart 本体 / NodeClass / NodePool は別 stack (eks-platform Phase C) で
# helm + kubectl_manifest provider 経由で deploy する設計。 本 module は AWS-side のみ。

module "karpenter" {
  source  = "terraform-aws-modules/eks/aws//modules/karpenter"
  version = "~> 20.0"

  cluster_name = var.cluster_name

  # Karpenter v1.0+ 用の権限セット (旧 v0.x の権限とは差分あり、 v1 で新たに必要な API call をカバー)。
  enable_v1_permissions = true

  # IRSA mode を使う (Phase A の eks-cluster module で OIDC provider が作成済)。
  # Pod Identity (alternative) は post-v1 で検討。
  enable_irsa            = true
  irsa_oidc_provider_arn = var.oidc_provider_arn

  # Karpenter Controller の K8s ServiceAccount。 Helm chart の default は karpenter/karpenter。
  irsa_namespace_service_accounts = ["${var.namespace}:${var.service_account_name}"]

  # Node IAM role:
  # system NG 用 role (eks-cluster module 内で自動作成) とは別に、 Karpenter NodePool の
  # node 用に専用 role を本 module 内で作成する。 名前は明示的に固定 (use_name_prefix=false)
  # して、 NodePool の nodeClassRef で参照しやすくする。
  create_node_iam_role            = true
  node_iam_role_name              = "${var.cluster_name}-karpenter-node"
  node_iam_role_use_name_prefix   = false
  node_iam_role_attach_cni_policy = true

  # 追加の inline policy が必要な場合は node_iam_role_additional_policies に渡す。 v1 では
  # default の AmazonEKSWorkerNodePolicy / AmazonEC2ContainerRegistryReadOnly /
  # AmazonSSMManagedInstanceCore / AmazonEKS_CNI_Policy が attach 済。
  node_iam_role_additional_policies = var.node_iam_role_additional_policies

  # Spot interruption + scheduled change の受信用 SQS。
  # default で enable、 EventBridge rules も module 内で配線される。
  # queue 名は cluster 名 prefix で、 Helm values の `settings.interruptionQueue` に渡す。

  tags = var.tags
}
