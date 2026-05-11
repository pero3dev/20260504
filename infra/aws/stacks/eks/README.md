# eks stack — Phase A (control plane + system NG)

ADR-0024 / ADR-0013 で確定した EKS 単一 cluster per env トポロジを 3 phase に分けて構築する。 本 stack は **Phase A** (control plane + 標準 addon + system NG)。

## Phase 構成

| Phase | stack | 範囲 | 状態 |
|---|---|---|---|
| **A** (本 stack) | `eks` | control plane + IAM + KMS + OIDC + 標準 addon + system NG + Karpenter discovery tag (cluster + node SG) | ⁴¹ で完了 |
| **B-1** | `eks-karpenter` | Karpenter Controller IRSA + Node IAM + Instance Profile + SQS interruption queue + EventBridge rules + private subnet discovery tag | ⁴² で完了 |
| **C-1** | `eks-platform` | Karpenter Helm + EC2NodeClass + NodePool | ⁴³ で完了 |
| **C-2** | `eks-platform` (本 stack に追加) | External Secrets Operator + ClusterSecretStore + AWS Load Balancer Controller | ⁴⁴ で完了 |
| **C-rest** | `eks-platform` (本 stack に追加) | Datadog + ArgoCD + Argo Rollouts + per-service IRSA + 他 stack の client SG attach | 後続 phase 予定 |

## Phase A で何を作るか

env あたり 1 cluster:

- **EKS control plane** `<env>-platform` (Kubernetes 1.31)
- **endpoint**: private (default、 全 env)。 kubectl access は SSM Session Manager + bastion 経由
- **secrets encryption**: kms stack の `<env>-secrets` で K8s Secret resource を CMK 暗号化
- **OIDC provider**: IRSA 用 (services の IAM role for service account)
- **control plane logs**: 全 5 type (api / audit / authenticator / controllerManager / scheduler) を CloudWatch に export、 90 日 retention
- **EKS Access Entries**: `API_AND_CONFIG_MAP` mode (ConfigMap aws-auth より管理容易な新方式)
- **標準 addon (most_recent = true)**: vpc-cni, kube-proxy, coredns, aws-ebs-csi-driver
- **EBS CSI driver IRSA role**: `<cluster>-ebs-csi-driver` (上流 sub-module で作成)
- **system managed node group**: 1 個、 ARM64 Graviton、 ON_DEMAND、 CoreDNS / Karpenter / ALB Controller の足場 (taint なし)
- **Karpenter discovery tag**: cluster + node SG に `karpenter.sh/discovery = <cluster_name>` を先付与

## per-env サイジング (system NG のみ、 application は Phase B Karpenter)

| env | instance | min | desired | max |
|---|---|---|---|---|
| dev | t4g.medium | 1 | 1 | 2 |
| staging | t4g.medium | 1 | 2 | 3 |
| prod | m7g.large | 2 | 2 | 4 |

prod は 各 AZ に 1 instance (multi-AZ) で system pod の HA を確保。 application 用容量は本 NG では持たず、 Phase B の Karpenter NodePool が Spot mix で動的に確保する設計。

## 依存

- `vpc` stack: `vpc_id`, `private_subnet_ids`
- `kms` stack: `secrets_key_arn` (EKS K8s Secret の at-rest 暗号化用)

## Apply 手順

bootstrap + iam-baseline + vpc + kms 完了後:

```bash
cd infra/aws/stacks/eks

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
- `cluster_id` → kubectl context / Helm release prefix
- `cluster_endpoint` + `cluster_certificate_authority_data` → kubeconfig 生成
- `oidc_provider_arn` → Phase B / C の IRSA role 作成で trust principal に渡す
- `node_security_group_id` → Phase C eks-platform で aurora_client / msk_client / redis_client SG を attach

## kubectl access (private endpoint)

EKS API が private なので外部 internet から直接 kubectl は叩けない。 経路:

### 1. SSM Session Manager + bastion (推奨)

VPC 内に bastion EC2 を立て、 SSM Session Manager で port forward:

```bash
# bastion EC2 を SSM 経由で起動 + 接続
aws ssm start-session \
  --target i-<bastion-id> \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["443"],"localPortNumber":["6443"]}'

# kubeconfig 生成
aws eks update-kubeconfig --name <env>-platform --region ap-northeast-1
# kubeconfig の server URL を https://localhost:6443 に書き換え
kubectl get nodes
```

### 2. 一時的な dev public access (allow-list)

dev 環境のみ、 `dev.tfvars` で:

```hcl
cluster_endpoint_public_access       = true
cluster_endpoint_public_access_cidrs = ["<office-ip>/32"]
```

設定して terraform apply。 操作完了後は false に戻す。

### 3. VPN (post-v1)

Cloudflare Access / OpenVPN / AWS VPN Client で VPC peering する経路。 ADR で別途検討。

## EKS Access Entries (新方式)

cluster 認証は EKS Access Entries で管理する想定:

```bash
# IAM role に cluster-admin 権限を与える例
aws eks create-access-entry \
  --cluster-name <env>-platform \
  --principal-arn arn:aws:iam::<account>:role/<admin-role>

aws eks associate-access-policy \
  --cluster-name <env>-platform \
  --principal-arn arn:aws:iam::<account>:role/<admin-role> \
  --policy-arn arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy \
  --access-scope type=cluster
```

terraform 化は post-v1 で別 PR (現状は CLI 運用、 Access Entries の terraform resource は v20 module の `access_entries` 引数で対応可能だが、 IAM role が iam-baseline 完成後にしか確定しないため後回し)。

## Karpenter 連携 (Phase B 向け先付与)

本 stack で:
- cluster + node SG に `karpenter.sh/discovery = <env>-platform` tag 付与
- Phase B で Karpenter NodePool が同 tag を selector に node 配置

vpc subnets にも同 tag が必要 (Karpenter が subnet を見つけるため)。 これは `vpc` stack の private subnets に Karpenter tag を追加する別 PR を Phase B に同梱予定。

## 既知の制約

- Phase A だけでは services が動かない (application 用 node がないため pod が pending)。 Phase B Karpenter で application node を確保するまで cluster は「使い物にならない」 状態
- system NG は taint なしで全 pod が schedule 可能、 application も Karpenter 不在時は system NG に流れ込む。 Phase B で application 用 NodePool に nodeSelector / tolerations を強制する設計
- Kubernetes 1.31 → 1.32 の minor upgrade は in-place で可能だが、 EKS 提供版の deprecation cycle (1 年) に追従する必要がある。 1 年に 1 回 `kubernetes_version` 更新 PR を出す
- prod の system NG cost ≒ m7g.large × 2 = ~$140/月。 application は別 cost (Phase B)
