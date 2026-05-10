# vpc stack

ADR-0024 で 3 番目に走らせる stack。 env ごとに 1 つ VPC を作る (env 依存 stack 第 1 号)。

## 何を作るか

env あたり:
- **VPC** `<env>-vpc` (CIDR: `10.{0|1|2}.0.0/16`、 dev/staging/prod で重複しない /16)
- **3 AZ** に展開:
  - `public_subnets` × 3 (`/24`、 ALB internet-facing 配置)
  - `private_subnets` × 3 (`/20`、 EKS node groups 配置)
  - `database_subnets` × 3 (`/24`、 Aurora / MSK / Redis 配置 + RDS DB Subnet Group 自動生成)
- **NAT Gateway**: prod は AZ ごとに 1 つ (3 個、 HA)、 dev/staging は 1 個共有 (cost 削減)
- **Internet Gateway** + route tables
- **S3 / DynamoDB gateway endpoints** (default ON、 free): private + database 両 route table に紐付け、 NAT 経由トラフィック削減

## CIDR 計画

| env | VPC CIDR | public | private | database |
|---|---|---|---|---|
| dev | `10.0.0.0/16` | `10.0.0.0/24`〜`10.0.2.0/24` | `10.0.16.0/20`〜`10.0.48.0/20` | `10.0.100.0/24`〜`10.0.102.0/24` |
| staging | `10.1.0.0/16` | `10.1.0.0/24`〜`10.1.2.0/24` | `10.1.16.0/20`〜`10.1.48.0/20` | `10.1.100.0/24`〜`10.1.102.0/24` |
| prod | `10.2.0.0/16` | `10.2.0.0/24`〜`10.2.2.0/24` | `10.2.16.0/20`〜`10.2.48.0/20` | `10.2.100.0/24`〜`10.2.102.0/24` |

将来の VPC peering / Transit Gateway 接続を見越し、 env 間で CIDR は overlap させない。

## Apply 手順

bootstrap + iam-baseline 完了後に走らせる。 env 依存 stack のため init は per-env partial backend config で。

```bash
cd infra/aws/stacks/vpc

# dev
terraform init -backend-config=envs/dev.backend.hcl
terraform plan -var-file=envs/dev.tfvars
terraform apply -var-file=envs/dev.tfvars

# staging (state を切り替えるので reconfigure 必須)
terraform init -reconfigure -backend-config=envs/staging.backend.hcl
terraform plan -var-file=envs/staging.tfvars
terraform apply -var-file=envs/staging.tfvars

# prod
terraform init -reconfigure -backend-config=envs/prod.backend.hcl
terraform plan -var-file=envs/prod.tfvars
terraform apply -var-file=envs/prod.tfvars
```

各 env 完了後、 outputs から `vpc_id` / `private_subnet_ids` / `database_subnet_ids` / `database_subnet_group_name` を控える。 後続 stack (eks / aurora / msk / elasticache) が `terraform_remote_state` で本 stack の state を読みに行くので、 outputs 名の変更は破壊的変更扱い。

## NAT Gateway 戦略

- **prod**: `single_nat_gateway = false` で各 AZ に 1 つずつ NAT GW (合計 3 個)。 1 AZ 障害時に他 AZ のトラフィックが影響を受けない HA 構成
- **dev / staging**: `single_nat_gateway = true` で全 AZ が 1 つの NAT GW を共有。 cost を 1/3 に削減 (NAT GW は固定 $33/月 + データ転送料)

## Gateway Endpoint

- S3: audit S3 / asset S3 / archive アクセスが NAT 経由しなくなる (cost 削減)
- DynamoDB: terraform state lock 取得が NAT 経由しなくなる
- 両方 free 機能。 default ON

## CI

`.github/workflows/terraform.yml` の `validate-vpc` job が PR ごとに `terraform fmt -check` + `terraform init -backend=false` + `terraform validate` を回す。 `terraform-aws-modules/vpc/aws` (~> 5.14) は `init` で download される。 AWS credential 不要。

## 後続で足したいもの (post-v1)

1. **Interface endpoints**: ECR API/dkr (image pull cost 削減)、 STS、 KMS、 Secrets Manager、 Cloud Map。 1 endpoint × 3 AZ ≒ $21/月、 必要な順に追加
2. **VPC Flow Logs**: SecOps 要件確定後に S3 行きで有効化 (cost ≒ Flow Log データ量 × $0.50/GB)
3. **NACL hardening**: database subnet の NACL を強化し、 NAT 経由 outbound を禁止 (Aurora / Redis から外部 internet 不要)
4. **Transit Gateway**: 複数 AWS account 化や on-premise VPN 接続が必要になったら導入
5. **IPv6 dual-stack**: AWS マネージド IPv6 prefix を割り当てて dual-stack 化

## 既知の制約

- `terraform-aws-modules/vpc/aws` v5.14 を依存。 上流 module の major version up は別 PR で `infra/aws/modules/vpc/main.tf` の `version` を上げる
- AZ 名は dynamic 取得 (`aws_availability_zones`) なので opt-in AZ (`ap-northeast-1b` 等) が account に追加されると順序が変わる可能性あり。 後続 stack が AZ 順依存している場合は注意
