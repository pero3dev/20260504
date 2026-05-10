# eks-karpenter stack — Karpenter の AWS-side prep を 1 箇所に集約 (eks Phase B-1)。
#
# 本 stack の責務:
#   1. Karpenter Controller IRSA + Node IAM role + Instance Profile + SQS queue + EventBridge rules
#      (modules/karpenter sub-module で実装、 上流 terraform-aws-modules/eks/aws//modules/karpenter)
#   2. vpc stack の private subnets に `karpenter.sh/discovery = <cluster_name>` tag を付与
#      (Karpenter が NodeClass.spec.subnetSelectorTerms で discovery する際の selector)
#
# 本 stack に**含まないもの** (eks-platform Phase C で実装):
#   - Karpenter Helm chart deploy
#   - EC2NodeClass / NodePool (K8s CRD)
#   - Network Policy / Pod Security Standards
#
# 設計判断: AWS リソース (terraform-native) と K8s リソース (helm/kubectl_manifest provider 必要) を
# 混在させると validate / plan の依存関係が複雑になる + cluster 起動前に validate できなくなる
# ため、 本 stack は AWS-side のみに限定する。 K8s 側は Phase C eks-platform で扱う。

# ----------------------------------------------------------------------------
# Remote state: eks (cluster name + OIDC provider arn) + vpc (subnet ids)
# ----------------------------------------------------------------------------

data "terraform_remote_state" "eks" {
  backend = "s3"

  config = {
    bucket = "inventory-platform-tfstate"
    key    = "aws/stacks/eks/${var.environment}.tfstate"
    region = "ap-northeast-1"
  }
}

data "terraform_remote_state" "vpc" {
  backend = "s3"

  config = {
    bucket = "inventory-platform-tfstate"
    key    = "aws/stacks/vpc/${var.environment}.tfstate"
    region = "ap-northeast-1"
  }
}

# ----------------------------------------------------------------------------
# Karpenter AWS resources
# ----------------------------------------------------------------------------

module "karpenter" {
  source = "../../modules/karpenter"

  cluster_name      = data.terraform_remote_state.eks.outputs.cluster_id
  oidc_provider_arn = data.terraform_remote_state.eks.outputs.oidc_provider_arn

  # Helm chart の default に合わせる (karpenter namespace + karpenter SA)。
  namespace            = "karpenter"
  service_account_name = "karpenter"

  tags = {}
}

# ----------------------------------------------------------------------------
# Subnet discovery tag
# ----------------------------------------------------------------------------
#
# Karpenter NodeClass の subnetSelectorTerms で `karpenter.sh/discovery = <cluster_name>` を使う。
# vpc module を変更すると VPC stack 全体に EKS 固有の関心が漏れるので、 本 stack で
# aws_ec2_tag を private subnets に対して個別付与する設計。
#
# 注意: aws_ec2_tag は subnet を「変更」する resource ではなく「追加 tag を付与する」 resource。
# vpc module 側の元 tag (kubernetes.io/role/internal-elb 等) には影響しない。

resource "aws_ec2_tag" "karpenter_discovery_subnet" {
  for_each = toset(data.terraform_remote_state.vpc.outputs.private_subnet_ids)

  resource_id = each.value
  key         = "karpenter.sh/discovery"
  value       = data.terraform_remote_state.eks.outputs.cluster_id
}
