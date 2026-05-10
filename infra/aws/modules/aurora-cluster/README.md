# module: aurora-cluster

`terraform-aws-modules/rds-aurora/aws` (~> 9.0) の薄ラッパー。 ADR-0005 で定義した 3 cluster トポロジ (hot-path / business / common-base) で再利用するため module 化。

## 何をやっているか

- engine = `aurora-postgresql` 16.x、 storage_encrypted = true、 KMS CMK 必須
- **manage_master_user_password = true** (AWS RDS managed master password、 自動 Secrets Manager + rotation)
- **client SG pattern**: 上流 module が cluster SG を作成、 ingress 5432 を `client_security_group_id` (引数) からのみ許可
- instance 構成: 引数 `instance_count` で writer + readers の総数を指定。 reader-1 を `promotion_tier = 0` で fail-over 第 1 候補に
- Performance Insights (7 日保持) + CloudWatch postgresql logs export (default ON)
- `apply_immediately = false` (本番障害誘発を避け、 maintenance window で apply)
- `deletion_protection` / `skip_final_snapshot` は env で切替可

## 範囲外 (本 module で扱わないもの)

- per-service の database / user / schema 作成: cluster 作成後の Flyway K8s Job で対応 (CLAUDE.md 方針)
- aurora_client_sg 自体の作成: stack 側で 1 つ作って全 cluster の ingress に追加する想定 (本 module は引数で受け取るのみ)
- read replica の AZ 配置の細粒度制御: 上流 module の default に任せる (Multi-AZ subnet group を渡せば自動分散)

## 入力

| 変数 | 説明 | デフォルト |
|---|---|---|
| `cluster_name` | cluster 名 (例: `prod-aurora-hotpath`) | (必須) |
| `engine_version` | Aurora PostgreSQL バージョン | `16.4` |
| `instance_class` | writer/reader 共通 instance class | (必須) |
| `instance_count` | writer + reader 総数 (1〜6) | (必須) |
| `vpc_id` | vpc stack output | (必須) |
| `db_subnet_group_name` | vpc stack output | (必須) |
| `kms_key_arn` | kms stack の aurora_key_arn | (必須) |
| `client_security_group_id` | aurora_client_sg ID (stack 側で作成) | (必須) |
| `master_username` | master user 名 | `aurora_master` |
| `initial_database_name` | placeholder DB 名 | `platform` |
| `deletion_protection` | dev=false / staging,prod=true | `true` |
| `skip_final_snapshot` | dev=true / staging,prod=false | `false` |
| `backup_retention_period` | 1〜35 日 | `35` |
| `preferred_backup_window` | UTC | `17:00-18:00` |
| `preferred_maintenance_window` | UTC | `sun:18:00-sun:19:00` |
| `performance_insights_enabled` | RDS Performance Insights | `true` |
| `tags` | 追加 tag | `{}` |

## 出力

`cluster_id` / `cluster_arn` / `cluster_endpoint` (writer) / `cluster_reader_endpoint` / `cluster_database_name` / `master_user_secret_arn` / `cluster_security_group_id`

## 使用例

```hcl
module "aurora_hotpath" {
  source = "../../modules/aurora-cluster"

  cluster_name             = "${var.environment}-aurora-hotpath"
  instance_class           = var.hotpath_instance_class
  instance_count           = var.hotpath_instance_count
  vpc_id                   = data.terraform_remote_state.vpc.outputs.vpc_id
  db_subnet_group_name     = data.terraform_remote_state.vpc.outputs.database_subnet_group_name
  kms_key_arn              = data.terraform_remote_state.kms.outputs.aurora_key_arn
  client_security_group_id = aws_security_group.aurora_client.id
  deletion_protection      = var.environment != "dev"
  skip_final_snapshot      = var.environment == "dev"
  backup_retention_period  = var.backup_retention_period
}
```

## post-apply での per-service DB セットアップ (別 PR で対応予定)

cluster 作成後、 各 service の専用 DB / schema / user を Flyway K8s Job で作成する想定:

```sql
-- admin 接続 (master_user_secret_arn から credential 取得) で実行
CREATE DATABASE inventory_core;
CREATE DATABASE master_data;

-- service 用 user (DB ごと別 user)
CREATE USER inventory_core_user WITH PASSWORD '<from secrets manager>';
GRANT ALL PRIVILEGES ON DATABASE inventory_core TO inventory_core_user;

-- Bridge model: tenant schema は service 側 application 起動時に動的に SET search_path
```

詳細は別 PR で `infra/aws/stacks/aurora/` の README に runbook 追加予定。

## 既知の制約

- 上流 module v9 系は instance 設定を `instances` map で持つが、 instance 名 ("1"/"2"/...) を変えると AWS 側で旧 instance を destroy + 新規作成するため、 down-size 時には注意 (instance 名は不変に保つ)
- `manage_master_user_password = true` で Secrets Manager に保管された secret は terraform state からは値が見えない。 admin が CLI 経由で取得する手順は stack README に記述
- CloudWatch postgresql logs は cost が無視できない (大規模 traffic 時)。 production で問題になったら filter 追加 / log group retention 短縮で対応
