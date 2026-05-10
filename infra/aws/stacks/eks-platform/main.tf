# eks-platform stack — EKS cluster の K8s-side platform pieces を deploy する集約 stack。
#
# Phase C のうち本 PR (⁴³) で扱う範囲:
#   - Karpenter Helm chart (CRD chart + Controller chart)
#   - default EC2NodeClass + default NodePool (kubectl_manifest 経由)
#
# Phase C で後追加予定 (別 PR、 各々独立 phase):
#   - External Secrets Operator + ClusterSecretStore
#   - AWS Load Balancer Controller (ALB Ingress)
#   - Datadog Agent DaemonSet (APM + logs + metrics)
#   - ArgoCD + Argo Rollouts
#   - per-service IRSA roles (13 services)
#   - aurora_client_sg / msk_client_sg / redis_client_sg を node SG に attach
#
# 本 stack は eks Phase A + eks-karpenter (Phase B-1) の outputs に依存する。
# AWS-side resource は eks-karpenter で完結しているので、 本 stack は K8s-side のみ。

# ----------------------------------------------------------------------------
# Remote state: eks (cluster connection info) + eks-karpenter (IRSA + queue)
# ----------------------------------------------------------------------------

data "terraform_remote_state" "eks" {
  backend = "s3"

  config = {
    bucket = "inventory-platform-tfstate"
    key    = "aws/stacks/eks/${var.environment}.tfstate"
    region = "ap-northeast-1"
  }
}

data "terraform_remote_state" "eks_karpenter" {
  backend = "s3"

  config = {
    bucket = "inventory-platform-tfstate"
    key    = "aws/stacks/eks-karpenter/${var.environment}.tfstate"
    region = "ap-northeast-1"
  }
}

# ----------------------------------------------------------------------------
# Karpenter — CRD chart (must precede the controller chart)
# ----------------------------------------------------------------------------
#
# Karpenter v1 では CRD は別 chart `karpenter-crd` として提供される。 Controller chart
# (karpenter) と同じ OCI registry (public.ecr.aws/karpenter) に公開されている。
# 順序: karpenter-crd → karpenter Controller → kubectl_manifest (NodeClass / NodePool)。

resource "helm_release" "karpenter_crd" {
  name             = "karpenter-crd"
  namespace        = "kube-system"
  create_namespace = false

  repository = "oci://public.ecr.aws/karpenter"
  chart      = "karpenter-crd"
  version    = var.karpenter_chart_version

  # CRD chart は values 不要 (CRD のみ install)。

  # CRD chart の install / upgrade は plan-time 検証されないため、 race を避けるため
  # `wait = true` で readiness を待つ。 実 apply 時のみ effect、 validate には影響しない。
  wait    = true
  timeout = 300
}

# ----------------------------------------------------------------------------
# Karpenter — Controller chart
# ----------------------------------------------------------------------------

resource "helm_release" "karpenter" {
  name             = "karpenter"
  namespace        = "karpenter"
  create_namespace = true

  repository = "oci://public.ecr.aws/karpenter"
  chart      = "karpenter"
  version    = var.karpenter_chart_version

  # IRSA: ServiceAccount に annotation を打ち、 eks-karpenter stack で作った Controller IRSA role を bind。
  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = data.terraform_remote_state.eks_karpenter.outputs.controller_iam_role_arn
  }

  # cluster identity: Karpenter Controller が EC2 launch 時に cluster 名 + endpoint を tag に書き込む。
  set {
    name  = "settings.clusterName"
    value = data.terraform_remote_state.eks.outputs.cluster_id
  }

  set {
    name  = "settings.clusterEndpoint"
    value = data.terraform_remote_state.eks.outputs.cluster_endpoint
  }

  # interruption queue: spot 中断 / instance state change を SQS から拾い、 Karpenter Controller が
  # node を pre-emptively drain する。 eks-karpenter stack で作った queue 名を渡す。
  set {
    name  = "settings.interruptionQueue"
    value = data.terraform_remote_state.eks_karpenter.outputs.interruption_queue_name
  }

  # Controller を system NG (taint なし) に schedule させる nodeSelector。
  # Phase A の system NG には label `node-role.platform/system = true` が打ってあるのでそれを使う。
  set {
    name  = "controller.nodeSelector.node-role\\.platform/system"
    value = "true"
  }

  # Karpenter Controller の resource limit。 cluster size に対して固定で十分小さい。
  set {
    name  = "controller.resources.requests.cpu"
    value = "200m"
  }
  set {
    name  = "controller.resources.requests.memory"
    value = "256Mi"
  }
  set {
    name  = "controller.resources.limits.cpu"
    value = "1"
  }
  set {
    name  = "controller.resources.limits.memory"
    value = "1Gi"
  }

  # 高可用性: Controller を 2 replica で動かす (1 active + 1 standby、 leader election)。
  set {
    name  = "replicas"
    value = "2"
  }

  # CRD は別 chart で先 install、 ここでは skip。
  set {
    name  = "crds.skipCrds"
    value = "true"
  }

  wait    = true
  timeout = 600

  depends_on = [helm_release.karpenter_crd]
}

# ----------------------------------------------------------------------------
# Default EC2NodeClass — 起動する EC2 instance の AMI / IAM role / subnet / SG を決める
# ----------------------------------------------------------------------------
#
# kubectl_manifest を使うのは、 EC2NodeClass / NodePool が Karpenter の CRD であり、
# helm_release.karpenter_crd で install されるまで存在しないため。 hashicorp/kubernetes の
# kubernetes_manifest は plan-time CRD discovery が必要 → Helm-then-manifest pattern と相性悪い。
# gavinbunney/kubectl は schema validation を apply 時に遅延するため本 case 向け。

resource "kubectl_manifest" "default_nodeclass" {
  yaml_body = yamlencode({
    apiVersion = "karpenter.k8s.aws/v1"
    kind       = "EC2NodeClass"
    metadata = {
      name = "default"
    }
    spec = {
      # AL2023 = Amazon Linux 2023 (Karpenter v1 default、 v0.x の AL2 と区別)。
      amiFamily = "AL2023"

      # IAM role (instance profile は Karpenter Controller が role 名から自動解決)。
      role = data.terraform_remote_state.eks_karpenter.outputs.node_iam_role_name

      # subnet 探索: vpc stack の private subnets に eks-karpenter stack で付与した tag を selector に使う。
      subnetSelectorTerms = [
        {
          tags = {
            "karpenter.sh/discovery" = data.terraform_remote_state.eks.outputs.cluster_id
          }
        },
      ]

      # SG 探索: eks Phase A で node SG に同 tag が付いている (cluster + node SG)。
      securityGroupSelectorTerms = [
        {
          tags = {
            "karpenter.sh/discovery" = data.terraform_remote_state.eks.outputs.cluster_id
          }
        },
      ]

      # AMI 探索: AL2023 の最新 EKS-optimized AMI を Karpenter が自動選択する。
      # 明示固定したい場合は amiSelectorTerms を追加。 v1 ではこの field 必須化された。
      amiSelectorTerms = [
        {
          alias = "al2023@latest"
        },
      ]

      # tag: Karpenter が起動する EC2 instance に付与。 cost allocation + 監査 + Datadog filter 用。
      tags = {
        "Project"     = "inventory-platform"
        "Environment" = var.environment
        "ManagedBy"   = "karpenter"
      }
    }
  })

  depends_on = [helm_release.karpenter]
}

# ----------------------------------------------------------------------------
# Default NodePool — どの workload にどんな node を割り当てるかの policy
# ----------------------------------------------------------------------------

resource "kubectl_manifest" "default_nodepool" {
  yaml_body = yamlencode({
    apiVersion = "karpenter.sh/v1"
    kind       = "NodePool"
    metadata = {
      name = "default"
    }
    spec = {
      template = {
        spec = {
          # NodeClass 参照 (上で定義した default)。
          nodeClassRef = {
            group = "karpenter.k8s.aws"
            kind  = "EC2NodeClass"
            name  = "default"
          }

          # 要件: arm64 / linux / on-demand+spot / multi-AZ。
          # spot 優先で cost 削減、 spot 不在時に on-demand fallback (Karpenter default 挙動)。
          requirements = [
            {
              key      = "kubernetes.io/arch"
              operator = "In"
              values   = [var.nodepool_default_arch]
            },
            {
              key      = "kubernetes.io/os"
              operator = "In"
              values   = ["linux"]
            },
            {
              key      = "karpenter.sh/capacity-type"
              operator = "In"
              values   = var.nodepool_default_capacity_types
            },
            # instance category: general-purpose (m, c) を許可、 db/storage 系 (r, i, d) は除外。
            # workload pattern が確定したら別 NodePool で r/i/d を混ぜる検討。
            {
              key      = "karpenter.k8s.aws/instance-category"
              operator = "In"
              values   = ["c", "m"]
            },
            # instance generation: 6+ (Graviton 6gen 以降)。 古い世代は cost 効率悪い。
            {
              key      = "karpenter.k8s.aws/instance-generation"
              operator = "Gt"
              values   = ["5"]
            },
          ]

          # node 自体の expiration: 1 週間で再生成 (security patch + image refresh)。
          # 進行中 pod は graceful drain される (default 30 秒)。
          expireAfter = "168h"
        }
      }

      # 全体上限: env ごとの安全枠。 cpu / memory limit を超える pending pod が出たら schedule 拒否。
      limits = {
        cpu    = var.nodepool_default_cpu_limit
        memory = var.nodepool_default_memory_limit
      }

      # disruption (consolidation): underutilized node を別 node に詰め直す + empty node を消す。
      # consolidateAfter = 30s で過剰反応も避けつつ rapid rebalance。
      disruption = {
        consolidationPolicy = "WhenEmptyOrUnderutilized"
        consolidateAfter    = "30s"
      }
    }
  })

  depends_on = [
    kubectl_manifest.default_nodeclass,
    helm_release.karpenter,
  ]
}
