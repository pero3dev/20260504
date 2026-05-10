# bootstrap が用意した S3 backend を使う。 env 非依存 stack のため state key は固定 "main"。
# bootstrap 適用前に本 stack を init すると AWS API エラーになるので、 必ず bootstrap 完了後に init する。
# CI は `-backend=false` で provider plugin だけ取得して validate するため backend 不要。

terraform {
  backend "s3" {
    bucket         = "inventory-platform-tfstate"
    key            = "aws/stacks/iam-baseline/main.tfstate"
    region         = "ap-northeast-1"
    dynamodb_table = "inventory-platform-tflock"
    encrypt        = true
    kms_key_id     = "alias/tfstate"
  }
}
