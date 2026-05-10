# bootstrap stack

ADR-0024 に基づく、 全 stack の前提となる Terraform state インフラを 1 度だけ作る stack。

## 何を作るか

- **S3 バケット** `inventory-platform-tfstate` (versioning ON / KMS 暗号化 / public block / 非current 90 日保持 / `prevent_destroy`)
- **DynamoDB lock table** `inventory-platform-tflock` (PAY_PER_REQUEST / KMS / PITR / `prevent_destroy`)
- **KMS CMK** `alias/tfstate` (rotation ON / 削除待機 30 日 / 本 stack 時点では account root 全権)

env 非依存 (env 横断の共有インフラ) で 1 AWS account あたり **1 回限り** 走らせる。 stack 単位で見ると state は `aws/stacks/bootstrap/main.tfstate` の 1 ファイルだけ。

## 初回 apply の特殊手順 (chicken-and-egg)

本 stack は「state を保管するインフラ」 を作るため、 初回は state 保管先がまだ存在しない。 以下の 2 段階で実行する。

### Step 1. local state で初回 apply

```bash
cd infra/aws/stacks/bootstrap

# backend.tf は backend ブロック未定義 (コメントアウト済み) のため、 local state で動く。
terraform init
terraform plan
terraform apply
```

成功すると `inventory-platform-tfstate` バケットと lock table が作られる。 同時に `terraform.tfstate` がローカルに生成される (gitignore 済 — `.gitignore` の `*.tfstate` で除外)。

### Step 2. 自分自身の state を S3 backend へ migrate

```bash
# backend.tf のコメントを外し、 backend "s3" ブロックを有効化してから:
terraform init -migrate-state
```

prompt で 「Do you want to copy existing state to the new backend?」 → `yes` 入力。 これでローカル state が S3 上の `aws/stacks/bootstrap/main.tfstate` に移動する。

ローカルの `terraform.tfstate` は **削除する** (S3 と乖離した古い state を残すと事故の元):

```bash
rm terraform.tfstate terraform.tfstate.backup
```

### Step 3. 動作確認

```bash
terraform plan
```

が「No changes」 で帰ってくれば migration 成功。 以降の `apply` は通常通り S3 backend を使う。

## 後続 stack のセットアップ

bootstrap が終われば、 以後の stack は最初から S3 backend を使える。 各 stack の `backend.tf` には:

```hcl
terraform {
  backend "s3" {
    bucket         = "inventory-platform-tfstate"
    key            = "aws/stacks/<stack-name>/<env>.tfstate"
    region         = "ap-northeast-1"
    dynamodb_table = "inventory-platform-tflock"
    encrypt        = true
    kms_key_id     = "alias/tfstate"
  }
}
```

を書く。 env を切り替えるときは `key` の `<env>` 部分だけ変える partial backend config を `envs/<env>.backend.hcl` に置き、 `terraform init -backend-config=envs/<env>.backend.hcl` で部分注入する想定。

## 削除

bootstrap stack は **削除しない**。 全 stack の lock / state を保持しているため、 destroy = 全環境破壊となる。 万一意図的な廃棄が必要な場合の手順:

1. 全 env の全 stack を逆順 destroy
2. `infra/aws/stacks/bootstrap/main.tf` の `prevent_destroy` を一時的に `false` に変更
3. `terraform destroy`
4. local state 化に戻して再度 `prevent_destroy = true` に戻すコミット

通常運用ではこの手順を踏むことは無い。 `prevent_destroy` を外す PR は SRE / Platform レビュー必須。

## CI

`.github/workflows/terraform.yml` の `validate-bootstrap` job が PR ごとに `terraform fmt -check` + `terraform init -backend=false` + `terraform validate` を回す。 AWS credential 不要で provider plugin の download だけで完結。

## 既知の制約

- 現状の KMS key policy は account root 全権のみ。 `iam-baseline` stack 完成後に「terraform deploy role のみ kms:Decrypt 可」 「読み取り専用 role は decrypt 不可」 のような細粒度に絞る別 PR を出す
- multi-region 化 (大阪に bootstrap を持つ DR 構成) は未対応。 `terraform.tfvars.example` にハコだけ用意してある
- 単一 AWS account 前提 (ADR-0024 の v1 方針)。 multi-account 化時は本 stack を「親 account」 + per-env account の 2 段に分ける ADR が別途必要
