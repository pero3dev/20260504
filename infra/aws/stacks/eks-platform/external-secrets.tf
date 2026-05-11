# External Secrets Operator (ESO) — AWS Secrets Manager / Parameter Store の secret を
# K8s Secret に同期する。 services は K8s Secret を envFrom/volumeMount で読むだけで済む。
#
# 構成:
#   - Controller IRSA: 「Secrets Manager 全 secret 読み取り + KMS Decrypt」 を持つ
#   - Helm chart 0.10.x (charts.external-secrets.io)
#   - ClusterSecretStore `aws-secretsmanager`: services manifest から refrenceName で参照
#
# services 利用例 (manifest 側):
#   apiVersion: external-secrets.io/v1beta1
#   kind: ExternalSecret
#   metadata: { name: redis-auth, namespace: inventory-read-model }
#   spec:
#     refreshInterval: 1h
#     secretStoreRef: { kind: ClusterSecretStore, name: aws-secretsmanager }
#     target: { name: redis-auth }
#     data:
#       - secretKey: REDIS_PASSWORD
#         remoteRef: { key: <env>/platform/redis/auth-token, property: auth_token }

# ----------------------------------------------------------------------------
# IRSA role for ESO Controller
# ----------------------------------------------------------------------------

module "external_secrets_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.50"

  role_name = "${data.terraform_remote_state.eks.outputs.cluster_id}-external-secrets"

  attach_external_secrets_policy = true
  # secret 読み取り対象: cluster 内 services が使う全 secret。 ワイルドカードで広めに付与し、
  # SecretStore 側で実際の対象を絞る (cluster-scoped でも secret 単位の grant は煩雑)。
  external_secrets_secrets_manager_arns = ["*"]
  external_secrets_ssm_parameter_arns   = ["*"]
  # KMS Decrypt は kms stack の <env>-secrets key で暗号化された secret を読むため必須。
  external_secrets_kms_key_arns = ["*"]
  # ESO Controller は read-only。 secret 書込み (create/update/delete) は別 IRSA で。
  external_secrets_secrets_manager_create_permission = false

  oidc_providers = {
    main = {
      provider_arn               = data.terraform_remote_state.eks.outputs.oidc_provider_arn
      namespace_service_accounts = ["external-secrets:external-secrets"]
    }
  }
}

# ----------------------------------------------------------------------------
# Helm release — external-secrets chart
# ----------------------------------------------------------------------------

resource "helm_release" "external_secrets" {
  name             = "external-secrets"
  namespace        = "external-secrets"
  create_namespace = true

  repository = "https://charts.external-secrets.io"
  chart      = "external-secrets"
  version    = var.external_secrets_chart_version

  values = [yamlencode({
    # IRSA role を ServiceAccount annotation で bind
    serviceAccount = {
      annotations = {
        "eks.amazonaws.com/role-arn" = module.external_secrets_irsa.iam_role_arn
      }
    }

    # CRD (ExternalSecret / ClusterSecretStore / SecretStore 等) を chart で install
    installCRDs = true

    # Controller HA。 leader election で 1 active + 1 standby。
    replicaCount = 2

    # Controller / Webhook / CertController を system NG に固定
    nodeSelector = {
      "node-role.platform/system" = "true"
    }
    webhook = {
      nodeSelector = {
        "node-role.platform/system" = "true"
      }
    }
    certController = {
      nodeSelector = {
        "node-role.platform/system" = "true"
      }
    }

    # resource limit (cluster サイズに対して固定で十分小さい)
    resources = {
      requests = { cpu = "100m", memory = "128Mi" }
      limits   = { cpu = "500m", memory = "512Mi" }
    }
  })]

  wait    = true
  timeout = 600
}

# ----------------------------------------------------------------------------
# ClusterSecretStore — AWS Secrets Manager
# ----------------------------------------------------------------------------
#
# auth field を省略すると ESO は controller pod の IRSA credential (上で bind した
# external_secrets_irsa role) を使う。 services 側は ExternalSecret の secretStoreRef.name
# で本 store を参照するだけ。

resource "kubectl_manifest" "secret_store_aws_secretsmanager" {
  yaml_body = yamlencode({
    apiVersion = "external-secrets.io/v1beta1"
    kind       = "ClusterSecretStore"
    metadata = {
      name = "aws-secretsmanager"
    }
    spec = {
      provider = {
        aws = {
          service = "SecretsManager"
          region  = var.region
        }
      }
    }
  })

  depends_on = [helm_release.external_secrets]
}

# ----------------------------------------------------------------------------
# ClusterSecretStore — AWS SSM Parameter Store (補助)
# ----------------------------------------------------------------------------
#
# 非 secret 設定 (例: feature flag、 endpoint URL) は Parameter Store の方が cost 効率良い。
# Secrets Manager と Parameter Store の使い分けは services 側 manifest で判断。

resource "kubectl_manifest" "secret_store_aws_ssm" {
  yaml_body = yamlencode({
    apiVersion = "external-secrets.io/v1beta1"
    kind       = "ClusterSecretStore"
    metadata = {
      name = "aws-ssm"
    }
    spec = {
      provider = {
        aws = {
          service = "ParameterStore"
          region  = var.region
        }
      }
    }
  })

  depends_on = [helm_release.external_secrets]
}
