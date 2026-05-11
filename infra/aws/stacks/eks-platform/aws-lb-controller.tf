# AWS Load Balancer Controller — Kubernetes Ingress/Service の type=LoadBalancer を
# AWS ALB / NLB に変換する Controller。 services 側は Ingress を作るだけで、 ALB が自動作成される。
#
# 構成:
#   - Controller IRSA: ALB / NLB / TG / SG の作成・更新権限 (canned policy)
#   - Helm chart 1.10.x (aws.github.io/eks-charts)
#   - clusterName / vpcId / region values を結線
#
# services 利用例 (Ingress 側):
#   apiVersion: networking.k8s.io/v1
#   kind: Ingress
#   metadata:
#     name: api
#     annotations:
#       alb.ingress.kubernetes.io/scheme: internet-facing
#       alb.ingress.kubernetes.io/target-type: ip
#       alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
#       alb.ingress.kubernetes.io/certificate-arn: <acm-cert-arn>
#   spec:
#     ingressClassName: alb
#     rules:
#       - host: api.example.com
#         http: { paths: [...] }

# ----------------------------------------------------------------------------
# IRSA role for AWS Load Balancer Controller
# ----------------------------------------------------------------------------

module "aws_lb_controller_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.50"

  role_name = "${data.terraform_remote_state.eks.outputs.cluster_id}-aws-lb-controller"

  # 上流 sub-module の canned policy。 ELBv2 / EC2 / WAF / Shield / ACM の必要 permission を持つ。
  attach_load_balancer_controller_policy = true

  oidc_providers = {
    main = {
      provider_arn               = data.terraform_remote_state.eks.outputs.oidc_provider_arn
      namespace_service_accounts = ["kube-system:aws-load-balancer-controller"]
    }
  }
}

# ----------------------------------------------------------------------------
# Helm release — aws-load-balancer-controller
# ----------------------------------------------------------------------------

resource "helm_release" "aws_lb_controller" {
  name      = "aws-load-balancer-controller"
  namespace = "kube-system"

  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  version    = var.aws_lb_controller_chart_version

  values = [yamlencode({
    # cluster 識別子 + ALB tag に使われる
    clusterName = data.terraform_remote_state.eks.outputs.cluster_id

    # ServiceAccount は chart が作成、 IRSA annotation を打つ。
    serviceAccount = {
      create = true
      name   = "aws-load-balancer-controller"
      annotations = {
        "eks.amazonaws.com/role-arn" = module.aws_lb_controller_irsa.iam_role_arn
      }
    }

    # ALB / NLB を VPC subnet に配置するため vpc_id 必須。 subnet 自動 discovery は
    # vpc stack で付与した `kubernetes.io/role/elb` / `kubernetes.io/role/internal-elb`
    # tag を見て行われる (本 stack で追加 tag 操作は不要)。
    vpcId  = data.terraform_remote_state.vpc.outputs.vpc_id
    region = var.region

    # HA: 2 replica + leader election (chart default で enabled)
    replicaCount = 2

    # system NG に固定
    nodeSelector = {
      "node-role.platform/system" = "true"
    }

    # resource limit
    resources = {
      requests = { cpu = "100m", memory = "128Mi" }
      limits   = { cpu = "500m", memory = "512Mi" }
    }

    # cert-manager 連携は使わず chart 内蔵の self-signed webhook cert を使用
    # (cert-manager 別途導入を避ける、 post-v1 で必要なら入れる)
    enableCertManager = false
  })]

  wait    = true
  timeout = 600
}
