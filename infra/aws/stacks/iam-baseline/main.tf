# iam-baseline stack — account-level IAM の base を 1 度だけ作る (env 非依存)。
#
# 対象リソース:
#   - aws_iam_account_password_policy           : 全 IAM user 共通のパスワード方針
#   - aws_iam_openid_connect_provider (github)  : GitHub Actions が OIDC 経由で assume する用
#   - aws_iam_role (tf-deploy)                  : terraform apply を実行する deploy role
#   - aws_iam_role_policy_attachment (admin)    : 上記 role に AdministratorAccess (v1 暫定、 後で絞る)
#
# 影響範囲: 全 IAM user の認証要件 + 全 stack の terraform apply 経路。 削除すると
# CI からの apply 不能 + 全 IAM user パスワード変更必要 (危険)。 prevent_destroy 必須。
#
# v1 では deploy role は env 非分離で AdminAccess 1 本。 env 単位で分けるのは将来 PR
# (per-env trust + per-env permission boundary)。 現状は trust policy で
# `repo:<org>/<repo>:*` までスコープし、 他 repo / 他 org からの assume は不可とする。

# ----------------------------------------------------------------------------
# Account password policy
# ----------------------------------------------------------------------------

resource "aws_iam_account_password_policy" "this" {
  minimum_password_length        = var.password_min_length
  require_lowercase_characters   = true
  require_uppercase_characters   = true
  require_numbers                = true
  require_symbols                = true
  allow_users_to_change_password = true
  max_password_age               = var.password_max_age_days
  password_reuse_prevention      = var.password_reuse_prevention
  hard_expiry                    = false
}

# ----------------------------------------------------------------------------
# GitHub Actions OIDC provider
# ----------------------------------------------------------------------------

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  thumbprint_list = [
    # GitHub Actions OIDC IdP の SSL 証明書 thumbprint。 AWS docs (2026-05 時点) の値。
    # GitHub 側のローテーションに追従するため Renovate / dependabot による定期 PR 更新を想定。
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
  ]

  # 削除すると CI からの assume が即時不能になり、 全 stack の deploy が止まる。
  lifecycle {
    prevent_destroy = true
  }
}

# ----------------------------------------------------------------------------
# Terraform deploy role (assumed by GitHub Actions via OIDC)
# ----------------------------------------------------------------------------

data "aws_iam_policy_document" "tf_deploy_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    # audience は GitHub OIDC の AWS 連携で必須の固定値。
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # 本 repo (org/repo) の workflow からのみ assume 許可。 他 repo の workflow からは不可。
    # ブランチや environment による更なる絞込は v1 では行わず、 後続 PR で per-env role 化する際に追加。
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_org}/${var.github_repo}:*"]
    }
  }
}

resource "aws_iam_role" "tf_deploy" {
  name               = var.tf_deploy_role_name
  assume_role_policy = data.aws_iam_policy_document.tf_deploy_trust.json

  description = "Assumed by GitHub Actions OIDC for terraform apply (all stacks, all envs in v1)."

  # 削除すると CI からの apply 全停止。 prevent_destroy 必須。
  lifecycle {
    prevent_destroy = true
  }
}

# v1: AdministratorAccess を attach。 全 stack を deploy できる広域権限。
# TODO (post-v1): IAM stack ごとに必要な action のみ allow する least-privilege policy に置換する。
# 暫定権限であることを明示するため description / README で TODO marker を残す。
resource "aws_iam_role_policy_attachment" "tf_deploy_admin" {
  role       = aws_iam_role.tf_deploy.name
  policy_arn = "arn:aws:iam::aws:policy/AdministratorAccess"
}
