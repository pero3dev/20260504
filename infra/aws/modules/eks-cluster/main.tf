# eks-cluster wrapper — terraform-aws-modules/eks/aws (~> 20.0) を薄ラップ。
#
# 含むもの (Phase A):
#   - EKS control plane (private endpoint default、 Kubernetes 1.31)
#   - cluster IAM service role (上流 module が作成)
#   - secrets encryption (KMS CMK 必須)
#   - OIDC provider (IRSA 用)
#   - 標準 EKS addon: vpc-cni / kube-proxy / coredns / aws-ebs-csi-driver
#   - EBS CSI driver の IRSA role (sub-module 経由)
#   - control plane 全 log type を CloudWatch に export
#   - system managed node group (1 個、 CoreDNS / Karpenter / ALB Controller の足場)
#   - Karpenter discovery tag (subnets / SG / cluster) を Phase B 向けに先付与
#
# 含まないもの (Phase B / C):
#   - Karpenter 本体 (Helm chart) と application node groups
#   - External Secrets Operator / Datadog / ArgoCD / Argo Rollouts / ALB Controller
#   - per-service IRSA roles
#   - Network Policy / Pod Security Standards 等の K8s リソース

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = var.cluster_name
  cluster_version = var.kubernetes_version

  vpc_id                   = var.vpc_id
  subnet_ids               = var.subnet_ids
  control_plane_subnet_ids = var.control_plane_subnet_ids

  # endpoint
  cluster_endpoint_private_access      = true
  cluster_endpoint_public_access       = var.cluster_endpoint_public_access
  cluster_endpoint_public_access_cidrs = var.cluster_endpoint_public_access_cidrs

  # secrets encryption (Kubernetes Secret resource を CMK で暗号化)
  create_kms_key = false # kms stack で provision 済みなので作らない
  cluster_encryption_config = {
    resources        = ["secrets"]
    provider_key_arn = var.cluster_kms_key_arn
  }

  # control plane logs (全 log type を CloudWatch に export、 90 日 retention)
  cluster_enabled_log_types = [
    "api",
    "audit",
    "authenticator",
    "controllerManager",
    "scheduler",
  ]
  cloudwatch_log_group_retention_in_days = 90

  # IRSA OIDC provider (services の IAM role for service account 用)
  enable_irsa = true

  # 認証は EKS Access Entries に統一 (新方式、 ConfigMap aws-auth より管理容易)
  authentication_mode = "API_AND_CONFIG_MAP"

  # 標準 EKS addon
  cluster_addons = {
    vpc-cni = {
      most_recent = true
    }
    kube-proxy = {
      most_recent = true
    }
    coredns = {
      most_recent = true
    }
    aws-ebs-csi-driver = {
      most_recent              = true
      service_account_role_arn = module.ebs_csi_irsa.iam_role_arn
    }
  }

  # system managed node group (1 個のみ、 application は Phase B Karpenter で管理)
  eks_managed_node_groups = {
    system = {
      name = "system"

      instance_types = var.system_node_instance_types
      capacity_type  = var.system_node_capacity_type
      ami_type       = "AL2023_ARM_64_STANDARD" # Graviton 想定

      min_size     = var.system_node_min_size
      max_size     = var.system_node_max_size
      desired_size = var.system_node_desired_size

      labels = {
        "node-role.platform/system" = "true"
      }

      # system NG は CoreDNS / Karpenter / ALB Controller を host するため taint なし。
      # application は Phase B で Karpenter NodePool に taint 付き、 system NG には載らない設計。
      taints = []
    }
  }

  # Karpenter discovery tag を node SG / cluster level に先付与 (Phase B で karpenter が探す)。
  # 上流 module の API: node_security_group_tags + tags (cluster 全体)
  node_security_group_tags = merge(
    var.tags,
    {
      "karpenter.sh/discovery" = var.cluster_name
    },
  )

  tags = merge(
    var.tags,
    {
      "karpenter.sh/discovery" = var.cluster_name
    },
  )
}

# EBS CSI driver の IRSA role (上流 sub-module で作成)
module "ebs_csi_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.50"

  role_name             = "${var.cluster_name}-ebs-csi-driver"
  attach_ebs_csi_policy = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:ebs-csi-controller-sa"]
    }
  }

  tags = var.tags
}
