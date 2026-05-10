# eks stack — env ごとに 1 EKS cluster を作る (Phase A: control plane + system NG)。
#
# Phase A 範囲 (本 stack):
#   - control plane (private endpoint、 Kubernetes 1.31)
#   - cluster IAM service role
#   - secrets encryption (kms stack の secrets_key_arn)
#   - OIDC provider (IRSA 用)
#   - 標準 EKS addon (vpc-cni / kube-proxy / coredns / aws-ebs-csi-driver)
#   - system managed node group (1 個、 CoreDNS / Karpenter / ALB Controller の足場)
#   - Karpenter discovery tag (Phase B 向け先付与)
#
# Phase B 範囲 (別 PR):
#   - Karpenter Helm chart deploy + NodeClass + NodePool
#   - application node groups (Karpenter 管理、 multi-AZ + Spot mix)
#   - Network Policy / Pod Security Standards
#
# Phase C 範囲 (別 stack: eks-platform):
#   - External Secrets Operator + ClusterSecretStore
#   - Datadog DaemonSet (APM + logs + metrics)
#   - ArgoCD + Argo Rollouts
#   - AWS Load Balancer Controller
#   - per-service IRSA roles + 他 stack の client SG attach (aurora_client / msk_client / redis_client)

# ----------------------------------------------------------------------------
# Remote state: vpc + kms
# ----------------------------------------------------------------------------

data "terraform_remote_state" "vpc" {
  backend = "s3"

  config = {
    bucket = "inventory-platform-tfstate"
    key    = "aws/stacks/vpc/${var.environment}.tfstate"
    region = "ap-northeast-1"
  }
}

data "terraform_remote_state" "kms" {
  backend = "s3"

  config = {
    bucket = "inventory-platform-tfstate"
    key    = "aws/stacks/kms/${var.environment}.tfstate"
    region = "ap-northeast-1"
  }
}

# ----------------------------------------------------------------------------
# EKS cluster (Phase A)
# ----------------------------------------------------------------------------

module "eks" {
  source = "../../modules/eks-cluster"

  cluster_name             = "${var.environment}-platform"
  kubernetes_version       = var.kubernetes_version
  vpc_id                   = data.terraform_remote_state.vpc.outputs.vpc_id
  subnet_ids               = data.terraform_remote_state.vpc.outputs.private_subnet_ids
  control_plane_subnet_ids = data.terraform_remote_state.vpc.outputs.private_subnet_ids
  cluster_kms_key_arn      = data.terraform_remote_state.kms.outputs.secrets_key_arn

  cluster_endpoint_public_access       = var.cluster_endpoint_public_access
  cluster_endpoint_public_access_cidrs = var.cluster_endpoint_public_access_cidrs

  system_node_min_size       = var.system_node_min_size
  system_node_max_size       = var.system_node_max_size
  system_node_desired_size   = var.system_node_desired_size
  system_node_instance_types = var.system_node_instance_types
  system_node_capacity_type  = var.system_node_capacity_type

  tags = {}
}
