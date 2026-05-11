# eks-karpenter stack — Karpenter AWS-side prep (eks Phase B-1)

ADR-0024 / eks Phase A の続き。 Karpenter 動作に必要な **AWS リソースのみ** を 1 stack に集約する。

## Phase 構成

| Phase | stack | 範囲 | 状態 |
|---|---|---|---|
| A | `eks` | control plane + IAM + KMS + OIDC + 標準 addon + system NG + Karpenter discovery tag (cluster + node SG) | ⁴¹ で完了 |
| **B-1 (本 stack)** | `eks-karpenter` | Karpenter Controller IRSA + Node IAM role + Instance Profile + SQS interruption queue + EventBridge rules + private subnet discovery tag | ⁴² で完了 |
| C-1 | `eks-platform` | Karpenter Helm + EC2NodeClass + NodePool | ⁴³ で完了 |
| C-2 | `eks-platform` (本 stack に追加) | External Secrets Operator + ClusterSecretStore + AWS Load Balancer Controller | ⁴⁴ で完了 |
| C-rest | `eks-platform` (本 stack に追加) | Datadog + ArgoCD + Argo Rollouts + per-service IRSA + 他 stack の client SG attach | 後続 phase 予定 |

## 設計判断: なぜ AWS-side と K8s-side を分けるか

Karpenter を 1 stack で全部扱うと:

- **provider 数が増える**: aws + helm + kubectl provider が必要 → init / validate コスト大
- **plan-time 依存が崩れやすい**: helm/kubectl は cluster apply 後にしか到達不可、 plan が cluster 起動を前提にしてしまう
- **state 結合が強くなる**: AWS リソース更新と K8s manifest 更新で同じ state を触る → drift / lock 競合の温床

そこで本 stack は AWS リソースのみに限定し、 K8s 側 (Helm chart deploy + NodeClass / NodePool) は Phase C `eks-platform` に分離する。 Phase A → B-1 → C の順で apply すれば一直線に Karpenter 利用可能になる設計。

## 何を作るか

env あたり:

- **Karpenter Controller IRSA role** `<cluster_name>-karpenter` (上流 sub-module の `iam_role_*`)
  - canned policy `enable_v1_permissions = true` (Karpenter v1.0+ 用権限)
  - trust policy: `<cluster>-OIDC` provider + `karpenter:karpenter` SA only
- **Node IAM role** `<cluster_name>-karpenter-node` (NodePool node 用)
  - 標準 4 policy (AmazonEKSWorkerNodePolicy / AmazonEC2ContainerRegistryReadOnly / AmazonSSMManagedInstanceCore / AmazonEKS_CNI_Policy)
- **Instance Profile** (上記 Node IAM role を wrap)
- **SQS interruption queue** `<cluster_name>` (Spot 中断 / instance state change の受信用)
- **EventBridge rules + targets**: spot interruption / instance state change / scheduled change を SQS に routing
- **private subnet tags**: `karpenter.sh/discovery = <cluster_name>` を 3 AZ 全てに付与 (NodeClass.spec.subnetSelectorTerms 用)

## 依存

- `eks` stack: `cluster_id`, `oidc_provider_arn`
- `vpc` stack: `private_subnet_ids` (subnet tag 用)

## Apply 手順

bootstrap + iam-baseline + vpc + kms + eks (Phase A) 完了後:

```bash
cd infra/aws/stacks/eks-karpenter

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

完了後 outputs から:
- `controller_iam_role_arn` → Phase C で Karpenter Helm chart の `serviceAccount.annotations[eks.amazonaws.com/role-arn]` に渡す
- `node_iam_role_name` → Phase C で EC2NodeClass.spec.role に渡す
- `interruption_queue_name` → Phase C で Helm values の `settings.interruptionQueue` に渡す

## Phase C で deploy する K8s リソース (参考)

本 stack の output を使って、 Phase C で以下を deploy する想定:

```yaml
# Karpenter Helm values の例
serviceAccount:
  annotations:
    eks.amazonaws.com/role-arn: <controller_iam_role_arn>

settings:
  clusterName: <cluster_id>
  interruptionQueue: <interruption_queue_name>
```

```yaml
# EC2NodeClass の例 (Phase C で deploy)
apiVersion: karpenter.k8s.aws/v1
kind: EC2NodeClass
metadata:
  name: default
spec:
  amiFamily: AL2023
  role: <node_iam_role_name>
  subnetSelectorTerms:
    - tags:
        karpenter.sh/discovery: <cluster_id>
  securityGroupSelectorTerms:
    - tags:
        karpenter.sh/discovery: <cluster_id>
```

```yaml
# NodePool の例 (Phase C で deploy、 application 用)
apiVersion: karpenter.sh/v1
kind: NodePool
metadata:
  name: default
spec:
  template:
    spec:
      requirements:
        - key: kubernetes.io/arch
          operator: In
          values: [arm64]                            # Graviton 優先
        - key: karpenter.sh/capacity-type
          operator: In
          values: [on-demand, spot]                  # mix で cost 効率
        - key: kubernetes.io/os
          operator: In
          values: [linux]
      nodeClassRef:
        group: karpenter.k8s.aws
        kind: EC2NodeClass
        name: default
  limits:
    cpu: "1000"
    memory: 1000Gi
  disruption:
    consolidationPolicy: WhenEmptyOrUnderutilized
    consolidateAfter: 30s
```

## 既知の制約 / 後続検討事項

- **Karpenter version 追従**: v1.0 以降は CRD の API group が `karpenter.k8s.aws` / `karpenter.sh` に変わった (v0.x は `karpenter.sh` のみ)。 上流 sub-module の `enable_v1_permissions = true` で v1 用権限を使用しているため、 Helm chart も v1 系 (chart version `~> 1.0`) を Phase C で固定する
- **Pod Identity 移行**: 現状 IRSA を使うが、 EKS Pod Identity (2023年末 GA) の方がシンプル。 v1 OK な状態が安定したら post-v1 で切替検討
- **node taints**: default NodePool に taint なしで application が schedule される。 system pod (Datadog DaemonSet 等) は system NG (Phase A) に nodeSelector で固定する設計。 application 専用 NodePool (taint 付き) と system pod 用 (taint なし) を分けるかは Phase C で再検討
