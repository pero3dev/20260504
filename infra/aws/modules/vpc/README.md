# module: vpc

`terraform-aws-modules/vpc/aws` (~> 5.14) の薄ラッパー。 ADR-0024 の wrapper 方針に従い、 プロジェクト共通の defaults を bind する。

## 何をやっているか

- 命名規則: `${var.environment}-vpc`
- DNS hostname / DNS support は両方有効
- Database subnets 用の dedicated route table と RDS subnet group を自動生成 (Aurora stack が参照)
- EKS subnet 識別 tag (`kubernetes.io/role/elb` = public、 `kubernetes.io/role/internal-elb` = private) を自動付与し、 AWS Load Balancer Controller の自動 discovery に対応
- S3 / DynamoDB **gateway endpoints** をオプションで作成 (default ON、 free)。 private + database 両 route table に紐付け、 NAT 経由トラフィックを大幅削減

## 入力

| 変数 | 説明 | デフォルト |
|---|---|---|
| `environment` | dev / staging / prod | (必須) |
| `vpc_cidr` | VPC CIDR | (必須) |
| `azs` | AZ 名 list | (必須) |
| `public_subnets` | public subnet CIDR list | (必須) |
| `private_subnets` | private compute subnet CIDR list | (必須) |
| `database_subnets` | private database subnet CIDR list | (必須) |
| `single_nat_gateway` | dev/staging で cost 削減目的に true | `false` |
| `enable_s3_gateway_endpoint` | S3 endpoint 作成 | `true` |
| `enable_dynamodb_gateway_endpoint` | DynamoDB endpoint 作成 | `true` |
| `tags` | 追加 tag | `{}` |

## 出力

vpc_id / vpc_cidr_block / azs / public_subnet_ids / private_subnet_ids / database_subnet_ids / database_subnet_group_name / public_route_table_ids / private_route_table_ids / database_route_table_ids / nat_public_ips

## 使用例

```hcl
module "vpc" {
  source = "../../modules/vpc"

  environment      = "dev"
  vpc_cidr         = "10.0.0.0/16"
  azs              = ["ap-northeast-1a", "ap-northeast-1c", "ap-northeast-1d"]
  public_subnets   = ["10.0.0.0/24",  "10.0.1.0/24",  "10.0.2.0/24"]
  private_subnets  = ["10.0.16.0/20", "10.0.32.0/20", "10.0.48.0/20"]
  database_subnets = ["10.0.100.0/24","10.0.101.0/24","10.0.102.0/24"]

  single_nat_gateway = true   # dev cost 削減

  tags = {
    Environment = "dev"
  }
}
```

## 既知の制約

- Interface endpoint (ECR / STS / KMS / Secrets Manager 等) は別モジュール / 別 stack で扱う。 cost (1 endpoint × 3 AZ ≒ $21/month) なので必要なものだけ後追いで足す
- IPv6 未対応。 dual stack 化は v2 の検討事項
- VPC Flow Logs はオプション化していない (cost 観点で v1 OFF、 SecOps 要件確定後に有効化検討)
