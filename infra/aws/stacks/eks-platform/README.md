# eks-platform stack — Phase C (K8s-side platform pieces)

ADR-0024 / eks Phase A + eks-karpenter Phase B-1 の続き。 EKS cluster の **K8s-side platform pieces** を deploy する集約 stack。

## Phase C 構成 (本 stack 全体のスコープ)

| 範囲 | 状態 |
|---|---|
| **Karpenter Helm chart (CRD + Controller) + default EC2NodeClass + default NodePool** | ⁴³ で完了 |
| **External Secrets Operator + ClusterSecretStore (Secrets Manager + SSM Parameter Store)** | ⁴⁴ で完了 |
| **AWS Load Balancer Controller (ALB Ingress)** | ⁴⁴ で完了 |
| Datadog Agent DaemonSet (APM + logs + metrics) | 後続 phase |
| ArgoCD + Argo Rollouts | 後続 phase |
| per-service IRSA roles (13 services) | 後続 phase |
| `aurora_client_sg` / `msk_client_sg` / `redis_client_sg` を node SG に attach | 後続 phase |

各 component は本 stack に追加していく方針 (新 stack を増やさない)。 stack 内 module 化 ([`infra/aws/modules/`](../../modules/)) はリソース複雑化に応じて検討。

## 何を作るか (⁴³ 時点)

- **`helm_release.karpenter_crd`**: Karpenter v1 の CRD chart (`oci://public.ecr.aws/karpenter/karpenter-crd`)。 namespace = `kube-system`。 Controller chart より先に install。
- **`helm_release.karpenter`**: Karpenter Controller chart。 namespace = `karpenter`。
  - IRSA: `serviceAccount.annotations[eks.amazonaws.com/role-arn]` = eks-karpenter stack の `controller_iam_role_arn`
  - cluster identity: `settings.{clusterName, clusterEndpoint}`
  - interruption queue: `settings.interruptionQueue` = eks-karpenter stack の `interruption_queue_name`
  - nodeSelector: `node-role.platform/system = true` (system NG に schedule)
  - replicas = 2 (leader election による HA)
  - resources: req 200m / 256Mi、 limit 1000m / 1Gi
- **`kubectl_manifest.default_nodeclass`**: default `EC2NodeClass`
  - `amiFamily: AL2023` + `amiSelectorTerms: alias=al2023@latest`
  - `role: <env>-platform-karpenter-node` (eks-karpenter stack の output)
  - `subnetSelectorTerms` + `securityGroupSelectorTerms` どちらも `karpenter.sh/discovery = <cluster_name>` で selector
  - tags: `Project / Environment / ManagedBy=karpenter`
- **`kubectl_manifest.default_nodepool`**: default `NodePool`
  - requirements: `arch=arm64` / `os=linux` / `capacity-type=[on-demand, spot]` / `instance-category=[c, m]` / `instance-generation > 5`
  - limits: env ごとの cpu / memory 上限 (dev=10/40Gi、 staging=50/200Gi、 prod=1000/4000Gi)
  - disruption: `WhenEmptyOrUnderutilized` + `consolidateAfter = 30s`
  - expireAfter: `168h` (1 週間で node 再生成、 security patch 適用)

### ⁴⁴ で追加: External Secrets Operator (`external-secrets.tf`)

- **`module.external_secrets_irsa`**: ESO Controller 用 IRSA role
  - `attach_external_secrets_policy = true` (canned policy)
  - `secrets_manager_arns = ["*"]` + `ssm_parameter_arns = ["*"]` + `kms_key_arns = ["*"]` (cluster scope の read 権限、 実 secret 単位の制限は SecretStore 側で)
  - `external_secrets_create_permission = false` (Controller は read-only、 secret 書込みは別 IRSA)
- **`helm_release.external_secrets`**: chart 0.10.7、 namespace = `external-secrets`、 replicaCount = 2 (HA)、 全 component (controller / webhook / certController) を system NG 固定、 IRSA を ServiceAccount annotation で bind
- **`kubectl_manifest.secret_store_aws_secretsmanager`**: `ClusterSecretStore` `aws-secretsmanager` (provider=SecretsManager + region)。 auth field 省略で Controller の IRSA credential を使用
- **`kubectl_manifest.secret_store_aws_ssm`**: 補助の `ClusterSecretStore` `aws-ssm` (provider=ParameterStore)。 非 secret 設定 (feature flag、 endpoint URL 等) の cost 効率向上

services 側からの利用 (ExternalSecret manifest 例):

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: redis-auth
  namespace: inventory-read-model
spec:
  refreshInterval: 1h
  secretStoreRef:
    kind: ClusterSecretStore
    name: aws-secretsmanager
  target:
    name: redis-auth
  data:
    - secretKey: REDIS_PASSWORD
      remoteRef:
        key: <env>/platform/redis/auth-token
        property: auth_token
```

### ⁴⁴ で追加: AWS Load Balancer Controller (`aws-lb-controller.tf`)

- **`module.aws_lb_controller_irsa`**: Controller 用 IRSA role
  - `attach_load_balancer_controller_policy = true` (canned policy、 ELBv2 / EC2 / WAF / Shield / ACM の必要 permission)
- **`helm_release.aws_lb_controller`**: chart 1.10.0、 namespace = `kube-system`、 replicaCount = 2 (HA)、 system NG 固定
  - `clusterName` = eks output `cluster_id`
  - `vpcId` = vpc output `vpc_id`
  - `region` = `var.region`
  - `enableCertManager = false` (chart 内蔵 self-signed webhook cert 使用、 cert-manager 別途導入を避ける)
- subnet 自動 discovery は vpc stack で付与した `kubernetes.io/role/elb` (public ALB) / `kubernetes.io/role/internal-elb` (private ALB) tag を Controller が解決するため、 本 stack 側で tag 操作は不要

services 側からの利用 (Ingress manifest 例):

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: api
  namespace: identity-broker
  annotations:
    alb.ingress.kubernetes.io/scheme: internet-facing      # public ALB
    alb.ingress.kubernetes.io/target-type: ip              # pod IP 直接
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/certificate-arn: <acm-cert-arn>
    alb.ingress.kubernetes.io/healthcheck-path: /actuator/health
spec:
  ingressClassName: alb
  rules:
    - host: api.example.com
      http:
        paths:
          - path: /v1
            pathType: Prefix
            backend:
              service: { name: identity-broker, port: { number: 8080 } }
```

## 依存

- `eks` stack (Phase A): `cluster_id` / `cluster_endpoint` / `cluster_certificate_authority_data` (provider config)
- `eks-karpenter` stack (Phase B-1): `controller_iam_role_arn` / `node_iam_role_name` / `interruption_queue_name`

## 必要 provider

- `hashicorp/aws ~> 5.70` — default tag のみ
- `hashicorp/helm ~> 2.17` — Karpenter chart deploy
- `gavinbunney/kubectl ~> 1.19` — Karpenter CRD (EC2NodeClass / NodePool) の apply
  - 補足: hashicorp/kubernetes の `kubernetes_manifest` は plan-time CRD discovery が必要だが、 Karpenter CRD は本 stack 内 helm_release で初めて install されるため使えない。 gavinbunney/kubectl は schema validation を apply 時まで遅延させる設計

## Apply 手順

bootstrap + iam-baseline + vpc + kms + eks (Phase A) + eks-karpenter (Phase B-1) 完了後:

```bash
cd infra/aws/stacks/eks-platform

# dev
terraform init -backend-config=envs/dev.backend.hcl
terraform plan -var-file=envs/dev.tfvars
terraform apply -var-file=envs/dev.tfvars

# staging
terraform init -reconfigure -backend-config=envs/staging.backend.hcl
terraform plan -var-file=envs/staging.tfvars
terraform apply -var-file=envs/staging.tfvars

# prod
terraform init -reconfigure -backend-config=envs/prod.backend.hcl
terraform plan -var-file=envs/prod.tfvars
terraform apply -var-file=envs/prod.tfvars
```

**重要**: 本 stack の apply には EKS cluster API への接続が必要。 helm + kubectl provider が `aws eks get-token` で token を取得するため、 操作者の AWS credential が **EKS Access Entries で当該 cluster の admin policy を持っている** 必要がある。

CI / CD 経路の場合は OIDC + IAM role で AssumeRole してから terraform を呼ぶ運用 (iam-baseline の `tf-deploy` role 想定)。 cluster 側で `tf-deploy` role に `AmazonEKSClusterAdminPolicy` を associate しておく必要あり。

## 動作確認

apply 後:

```bash
# Karpenter Controller pod が Running か
kubectl -n karpenter get pods

# EC2NodeClass / NodePool が登録されたか
kubectl get ec2nodeclasses default -o yaml
kubectl get nodepools default -o yaml

# 何か pending な application pod を作って Karpenter が node を起動するか試す:
kubectl run nginx --image=nginx --requests=cpu=2 --command -- sleep 1d
kubectl get pods nginx -o wide
# しばらく待つ (60-90 秒) → 新 node が起動して pending → Running に遷移
kubectl get nodes -L karpenter.sh/nodepool
```

Karpenter Controller log:

```bash
kubectl -n karpenter logs -l app.kubernetes.io/name=karpenter -f --tail=100
```

主な log 種別:
- `launching nodeclaim` : pending pod に対して新 node を起動
- `disrupting nodeclaim` : 余剰 / underutilized node を停止
- `interruption` : SQS から spot 中断通知を受信、 node を drain

## chart version 更新フロー

1. `var.karpenter_chart_version` を bump (例: `1.1.1` → `1.2.0`)
2. release notes を確認 (https://karpenter.sh/v1.2/upgrading/)
3. dev で `terraform apply` → 動作確認
4. staging → prod へ順次 apply

CRD chart と Controller chart は同じ version で揃える前提。 不揃いだと CRD schema が controller の期待と合わずに crashloop する。

## 既知の制約 / 後続検討事項

- **public.ecr.aws の auth 不要**: Karpenter chart は public ECR にあるので OCI authorization は不要。 helm provider は anonymous pull で動作
- **K8s manifest の drift 検知**: `terraform apply` 後に `kubectl edit` で手動変更しても terraform は気付けない (kubectl_manifest は最後 apply の YAML を state に持つだけ)。 ArgoCD 導入後は ArgoCD 側に管理を移譲する想定 (terraform からは ArgoCD Application 定義のみ管理)
- **default NodePool の taint なし**: 全 application pod が default NodePool に schedule される。 system pod (Datadog DaemonSet 等) は system NG (Phase A) に nodeSelector で固定する想定。 application 専用 NodePool に taint 付け / system pod 用 NodePool 分離は後続 phase で再検討
- **arch=arm64 固定**: legacy native binary (例: 一部の OEM library 同梱 service) が出てきた場合は amd64 NodePool を別 resource で追加。 現状 services 全部 OpenJDK + Spring Boot で arm64 動作確認済前提
- **prod の resource limit が大きすぎる懸念**: 1000 vCPU / 4000 GiB は理論上限。 実需要は Datadog で観測してから縮小。 過剰なら Karpenter は起動しないので over-provision の cost 影響はない (Karpenter は需要ベース起動なので unused capacity は 0)
