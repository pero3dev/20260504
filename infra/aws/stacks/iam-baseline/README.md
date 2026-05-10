# iam-baseline stack

ADR-0024 で 2 番目に走らせる stack。 全 IAM の base となる account-level リソースを 1 度だけ作る。

## 何を作るか

- **`aws_iam_account_password_policy`** — 全 IAM user 共通: 14 文字以上 / 大小英数記号必須 / 90 日 rotation / 24 個再利用禁止
- **`aws_iam_openid_connect_provider`** — GitHub Actions が OIDC で AWS にアクセスするための provider (URL: `https://token.actions.githubusercontent.com`)
- **`aws_iam_role`** `inventory-platform-tf-deploy` — terraform apply 用 deploy role
  - **trust**: 本 repo (`pero3dev/20260504`) の GitHub Actions workflow からのみ assume 可
  - **permission**: `AdministratorAccess` を attach (v1 暫定、 TODO で絞込み)
- prevent_destroy: OIDC provider と role 双方に有効、 削除すると CI からの apply 全停止

env 非依存 (account-level) で 1 AWS account あたり **1 回限り**走らせる。 state は `aws/stacks/iam-baseline/main.tfstate` の 1 ファイル。

## Apply 手順

bootstrap stack の後に走らせる。 backend は bootstrap が用意済みなので S3 backend がそのまま使える。

```bash
cd infra/aws/stacks/iam-baseline

terraform init
terraform plan
terraform apply
```

完了後、 outputs から GitHub OIDC provider ARN と deploy role ARN を控える。 他 stack の workflow / role 作成で参照する。

## GitHub Actions workflow からの使用例 (将来の `terraform-apply.yml`)

deploy role を assume するための workflow snippet:

```yaml
permissions:
  id-token: write   # OIDC token 取得に必須
  contents: read

jobs:
  apply:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::<account-id>:role/inventory-platform-tf-deploy
          aws-region: ap-northeast-1
      - uses: hashicorp/setup-terraform@v3
      - run: terraform -chdir=infra/aws/stacks/<stack> apply -auto-approve
```

実 apply workflow は別 PR で導入する。 本 stack はその前提となる role と OIDC provider のみを用意する。

## CI

`.github/workflows/terraform.yml` の `validate-iam-baseline` job が PR ごとに `terraform fmt -check` + `terraform init -backend=false` + `terraform validate` を回す。 AWS credential 不要。

## TODO (post-v1)

1. **deploy role の権限を least-privilege に絞る**: 現状 AdminAccess だが、 各 stack に必要な action のみ allow するカスタムポリシーへ。 stack ごとに必要 action を洗い出して permission policy を組み立てる
2. **per-env deploy role 分離**: `inventory-platform-{dev,staging,prod}-tf-deploy` の 3 役割に分け、 trust 条件で `repo:<org>/<repo>:environment:<env>` を要求して GitHub deployment environment と紐付け、 main branch から prod にしか deploy できないように staffing
3. **permission boundary**: deploy role に boundary policy を付け、 自分自身を破壊する apply (`iam-baseline` 自身の destroy 等) を物理的に封じる
4. **AWS Organizations SCP**: 親アカウント側で「特定リソース類の destroy は禁止」 等の SCP を別途設定 (multi-account 化と同時)
5. **IAM Access Analyzer 有効化**: 想定外の cross-account access を検知

## 既知の制約

- v1 では deploy role は単一 (env 横断 admin)。 「dev のミスが prod に影響しない」 保証は IAM 境界ではなく命名規則 (`<env>-` prefix) と operator discipline に依存
- GitHub OIDC thumbprint は固定値 hardcode。 GitHub 側で certificate rotation した場合に手動更新が必要 (Renovate 等で自動化検討)
- account password policy は SAML federation (Cognito) 経由ログインに **適用されない**。 SAML 経由 user は IdP 側のパスワード方針に従う。 本 stack の policy は AWS console 直接ログインする少数 break-glass IAM user 向け
