# kms stack — env ごとに 4 種のアプリ用 CMK を作る。
#
# 各 key は modules/kms-key 経由で生成 (rotation ON / 削除待機 30 日 / account root 政策)。
# alias 命名は `alias/<env>-<purpose>` の規則 (ADR-0024)。
#
# 用途別:
#   aurora    : 3 Aurora cluster (hot-path / business / common-base) の at-rest 暗号化共通
#   audit-s3  : audit-service の S3 Object Lock バケット (compliance mode 365 days)
#   secrets   : Secrets Manager で管理する全 secret 共通
#   ebs       : EKS node groups の EBS volume + PVC 共通
#
# 後続 stack 候補 (本 stack 範囲外、 必要時に追加):
#   - msk        : MSK 専用 CMK (v1 では AWS-managed key 利用、 SecOps 要件で別 CMK 化検討)
#   - elasticache: Redis at-rest 暗号化 (v1 では AWS-managed)

module "aurora_key" {
  source = "../../modules/kms-key"

  alias_name  = "alias/${var.environment}-aurora"
  description = "Aurora cluster encryption (3 clusters: hot-path / business / common-base) for ${var.environment}"
}

module "audit_s3_key" {
  source = "../../modules/kms-key"

  alias_name  = "alias/${var.environment}-audit-s3"
  description = "S3 Object Lock bucket encryption for audit records (Compliance mode 365 days) — ${var.environment}"
}

module "secrets_key" {
  source = "../../modules/kms-key"

  alias_name  = "alias/${var.environment}-secrets"
  description = "Secrets Manager encryption for all platform secrets in ${var.environment}"
}

module "ebs_key" {
  source = "../../modules/kms-key"

  alias_name  = "alias/${var.environment}-ebs"
  description = "EBS volume + PVC encryption for EKS node groups in ${var.environment}"
}
