# bootstrap が用意した S3 backend を使う。 env 依存 stack のため state key (= env) は
# `envs/<env>.backend.hcl` の partial config 経由で注入する。
#
# init 例:
#   terraform init -backend-config=envs/dev.backend.hcl
#   terraform init -backend-config=envs/staging.backend.hcl
#   terraform init -backend-config=envs/prod.backend.hcl
#
# CI は `-backend=false` で provider plugin だけ取得して validate するため、 partial 値は不要。

terraform {
  backend "s3" {
    bucket         = "inventory-platform-tfstate"
    region         = "ap-northeast-1"
    dynamodb_table = "inventory-platform-tflock"
    encrypt        = true
    kms_key_id     = "alias/tfstate"
    # key は per-env partial config で注入する (envs/<env>.backend.hcl)。
  }
}
