terraform {
  required_version = ">= 1.7.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.17"
    }
    # gavinbunney/kubectl は CRD が plan time に存在しなくても apply 時に解決するため、
    # Karpenter v1 CRD の EC2NodeClass / NodePool 適用に必要。
    # hashicorp/kubernetes の kubernetes_manifest は plan time CRD discovery が必要 → Helm 経由
    # CRD install と相性悪い。
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = "~> 1.19"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "inventory-platform"
      ManagedBy   = "terraform"
      Stack       = "eks-platform"
      Environment = var.environment
    }
  }
}

# Helm provider: cluster API token は aws CLI exec で都度取得する形式。
# このため operator (terraform 実行者) の AWS credential が EKS Access Entries で
# cluster-admin policy を持っている必要がある。 CI / CD 経路の場合は OIDC + IAM role
# で AssumeRole してから terraform を呼ぶ運用 (iam-baseline の tf-deploy role 想定)。
provider "helm" {
  kubernetes {
    host                   = data.terraform_remote_state.eks.outputs.cluster_endpoint
    cluster_ca_certificate = base64decode(data.terraform_remote_state.eks.outputs.cluster_certificate_authority_data)
    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args = [
        "eks",
        "get-token",
        "--cluster-name",
        data.terraform_remote_state.eks.outputs.cluster_id,
        "--region",
        var.region,
      ]
    }
  }
}

provider "kubectl" {
  host                   = data.terraform_remote_state.eks.outputs.cluster_endpoint
  cluster_ca_certificate = base64decode(data.terraform_remote_state.eks.outputs.cluster_certificate_authority_data)
  load_config_file       = false

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args = [
      "eks",
      "get-token",
      "--cluster-name",
      data.terraform_remote_state.eks.outputs.cluster_id,
      "--region",
      var.region,
    ]
  }
}
