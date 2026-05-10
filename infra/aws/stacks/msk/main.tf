# msk stack — env ごとに MSK Serverless cluster を 1 つ作る。
#
# 採用方針: MSK Serverless (Provisioned ではない)
#   - 200 MB/s ingress / 400 MB/s egress までは serverless で十分 (peak 11.6k TPS x ~3 events x 1 KB
#     = ~50 MB/s 想定、 上限の 1/4)
#   - broker sizing / storage / partition rebalancing 等の運用負荷ゼロ
#   - IAM auth 専用 (SASL/SCRAM、 mTLS、 unauthenticated は不可)、 architecture.md C4 と整合
#   - VPC private endpoint のみ、 public 経路なし (default 仕様)
#
# 構成:
#   - aws_msk_serverless_cluster: cluster 本体、 IAM auth 有効化
#   - msk_cluster_sg + msk_client_sg: elasticache / aurora と同 pattern (空 client SG を node が attach)
#   - aws_iam_policy_document (data): services の IRSA role に attach する template (実 attach は別 PR)
#
# Glue Schema Registry 連携:
#   - MSK 側に直接 binding する API なし
#   - client (Spring Kafka producer/consumer) が AWSGlueSchemaRegistry serializer を使い、 IRSA で
#     glue:GetSchema 等の権限を持つ形 (services 側 wire は eks-platform / 各 service IRSA stack で対応)
#
# Topic 作成は本 stack 範囲外:
#   - default で services は KafkaAdminClient.createTopics で auto-create する設定
#   - production では K8s Job (kafka-topics.sh) で明示的に作成する別 PR を予定

# ----------------------------------------------------------------------------
# Remote state: vpc
# ----------------------------------------------------------------------------

data "terraform_remote_state" "vpc" {
  backend = "s3"

  config = {
    bucket = "inventory-platform-tfstate"
    key    = "aws/stacks/vpc/${var.environment}.tfstate"
    region = "ap-northeast-1"
  }
}

# ----------------------------------------------------------------------------
# Security groups
# ----------------------------------------------------------------------------

resource "aws_security_group" "msk_client" {
  name_prefix = "${var.environment}-msk-client-"
  description = "Attach to EKS node group / pod that needs MSK access (${var.environment})"
  vpc_id      = data.terraform_remote_state.vpc.outputs.vpc_id

  # 空 SG (egress 制御は attach 側で実施)。 「この SG が attach されている = MSK に話しかけてよい」 マーカ。
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "msk_cluster" {
  name_prefix = "${var.environment}-msk-cluster-"
  description = "MSK cluster SG (${var.environment}). Ingress only from msk_client SG."
  vpc_id      = data.terraform_remote_state.vpc.outputs.vpc_id

  lifecycle {
    create_before_destroy = true
  }
}

# IAM SASL TLS port (MSK Serverless は 9098 のみ)。
resource "aws_security_group_rule" "msk_cluster_ingress_iam_sasl" {
  type                     = "ingress"
  from_port                = 9098
  to_port                  = 9098
  protocol                 = "tcp"
  security_group_id        = aws_security_group.msk_cluster.id
  source_security_group_id = aws_security_group.msk_client.id
  description              = "Kafka IAM SASL TLS from msk_client SG"
}

# ----------------------------------------------------------------------------
# MSK Serverless cluster
# ----------------------------------------------------------------------------

resource "aws_msk_serverless_cluster" "this" {
  cluster_name = "${var.environment}-platform-msk"

  vpc_config {
    subnet_ids         = data.terraform_remote_state.vpc.outputs.database_subnet_ids
    security_group_ids = [aws_security_group.msk_cluster.id]
  }

  client_authentication {
    sasl {
      iam {
        enabled = true
      }
    }
  }
}

# ----------------------------------------------------------------------------
# IAM policy document (services の IRSA role に attach する template)
#
# 本 stack では policy document の生成のみ。 実 attach は per-service IRSA stack で
# aws_iam_policy + aws_iam_role_policy_attachment を介して行う設計 (eks-platform / service IRSA)。
# ----------------------------------------------------------------------------

data "aws_iam_policy_document" "msk_client" {
  # Connect / DescribeCluster は cluster 単位の必要権限
  statement {
    sid    = "AllowMskClusterConnect"
    effect = "Allow"
    actions = [
      "kafka-cluster:Connect",
      "kafka-cluster:DescribeCluster",
      "kafka-cluster:AlterCluster",
    ]
    resources = [aws_msk_serverless_cluster.this.arn]
  }

  # Topic / Group の操作は wildcard cluster 配下で許可。 services は自 topic / group のみ
  # 触るので、 topic name レベルでの絞込みは IRSA stack で各 service ごとに行う。
  statement {
    sid    = "AllowMskTopicWriteRead"
    effect = "Allow"
    actions = [
      "kafka-cluster:DescribeTopic",
      "kafka-cluster:CreateTopic",
      "kafka-cluster:WriteData",
      "kafka-cluster:ReadData",
      "kafka-cluster:DescribeGroup",
      "kafka-cluster:AlterGroup",
    ]
    # MSK Serverless の topic / group ARN は cluster ARN suffix で構築。
    resources = [
      "${replace(aws_msk_serverless_cluster.this.arn, ":cluster/", ":topic/")}/*",
      "${replace(aws_msk_serverless_cluster.this.arn, ":cluster/", ":group/")}/*",
    ]
  }
}
