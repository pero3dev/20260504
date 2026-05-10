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
