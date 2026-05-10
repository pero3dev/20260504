# module: eks-cluster

`terraform-aws-modules/eks/aws` (~> 20.0) の薄ラッパー。 ADR-0024 並列フェーズ完了後の依存第 4 層 stack で使う。

## Phase A 範囲 (本 module で扱うもの)

- EKS control plane (Kubernetes 1.31、 private endpoint default)
- cluster IAM service role (上流 module が作成)
- secrets encryption (KMS CMK 必須、 `cluster_kms_key_arn` 引数)
- OIDC provider (IRSA 用、 `enable_irsa = true`)
- 標準 EKS addon: vpc-cni / kube-proxy / coredns / aws-ebs-csi-driver
- EBS CSI driver の IRSA role (上流 `iam-role-for-service-accounts-eks` sub-module で作成)
- control plane 全 log type を CloudWatch に export (90 日 retention)
- system managed node group 1 個 (CoreDNS / Karpenter / ALB Controller の足場、 taint なし)
- Karpenter discovery tag を node SG / cluster level に先付与 (Phase B で karpenter が読み取る)
- EKS Access Entries を `API_AND_CONFIG_MAP` mode で有効化 (ConfigMap aws-auth より管理容易)

## Phase A 範囲外 (Phase B / C で対応)

- **Phase B**: Karpenter (Helm chart deploy + NodeClass + NodePool)、 application node groups (Karpenter 管理)、 Network Policy / Pod Security Standards
- **Phase C**: External Secrets Operator / Datadog DaemonSet / ArgoCD / Argo Rollouts / AWS Load Balancer Controller、 per-service IRSA roles

## 入力

| 変数 | 説明 | デフォルト |
|---|---|---|
| `cluster_name` | EKS cluster 名 (例: `dev-platform`) | (必須) |
| `kubernetes_version` | Kubernetes version | `1.31` |
| `vpc_id` | vpc stack output | (必須) |
| `subnet_ids` | private compute subnets | (必須) |
| `control_plane_subnet_ids` | control plane ENI 配置用 (通常 = subnet_ids) | (必須) |
| `cluster_kms_key_arn` | kms stack の secrets_key_arn | (必須) |
| `cluster_endpoint_public_access` | public endpoint 有効化 | `false` |
| `cluster_endpoint_public_access_cidrs` | public 許可 CIDR | `["0.0.0.0/0"]` |
| `system_node_min_size` / `_max_size` / `_desired_size` | system NG sizing | `1` / `3` / `2` |
| `system_node_instance_types` | instance types list | `["t4g.medium"]` |
| `system_node_capacity_type` | `ON_DEMAND` or `SPOT` | `ON_DEMAND` |
| `tags` | 追加 tag | `{}` |

## 出力

`cluster_id` / `cluster_arn` / `cluster_endpoint` / `cluster_certificate_authority_data` / `cluster_oidc_issuer_url` / `oidc_provider_arn` / `cluster_security_group_id` / `node_security_group_id` / `ebs_csi_irsa_role_arn`

## 使用例

```hcl
module "eks" {
  source = "../../modules/eks-cluster"

  cluster_name             = "${var.environment}-platform"
  kubernetes_version       = "1.31"
  vpc_id                   = data.terraform_remote_state.vpc.outputs.vpc_id
  subnet_ids               = data.terraform_remote_state.vpc.outputs.private_subnet_ids
  control_plane_subnet_ids = data.terraform_remote_state.vpc.outputs.private_subnet_ids
  cluster_kms_key_arn      = data.terraform_remote_state.kms.outputs.secrets_key_arn

  system_node_min_size     = var.system_node_min_size
  system_node_max_size     = var.system_node_max_size
  system_node_desired_size = var.system_node_desired_size
}
```

## Karpenter 連携

本 module は Phase B で Karpenter が動く前提を整える:

- `karpenter.sh/discovery = <cluster_name>` tag を:
  - cluster 自身 (`tags`)
  - node SG (`node_security_group_tags`)
- これを Phase B の Karpenter NodePool が `instanceProfile.tags` selector で読み取って、 同じ tag が付いた SG / cluster 配下に node を配置する

vpc stack 側で subnet にも同 tag が付くように `karpenter.sh/discovery` tag を private subnets に追加する PR を Phase B で出す予定 (post-v1)。

## kubectl access

private endpoint default なので operator が kubectl で接続する経路は:

1. **SSM Session Manager + bastion EC2** (推奨): bastion 経由で kubectl を叩く
2. **VPN (Cloudflare Access / OpenVPN)**: post-v1 ADR で導入
3. **dev のみ public + IP allow-list**: `cluster_endpoint_public_access = true` + `cluster_endpoint_public_access_cidrs = ["<office-ip>/32"]` (dev tfvars で個別有効化)

EKS Access Entries (新方式) で IAM principal を cluster admin / view 等 mode に紐付ける。 ConfigMap aws-auth は読み取りで保持されるが、 management は Access Entries 側を優先する。

## 既知の制約

- 上流 module v20 系は v19 から API 変更が大きい (cluster_addons / authentication_mode 等)。 v20.x 系に固定 (Renovate 自動 PR で micro version 上げ、 major は手動 review)
- EKS Kubernetes version は 1 年で deprecated になるので、 1 年に 1 回 minor version 上げ PR が必要 (`kubernetes_version` 更新 → 各 addon の `most_recent = true` で自動追従)
- `most_recent = true` は addon を最新 EKS 提供版に上げ続けるが、 cluster version との互換マトリクスは EKS 側が保証。 ただし cluster_addons の plan diff が頻繁に出る点は許容
