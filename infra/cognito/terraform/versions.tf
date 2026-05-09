# Terraform / provider バージョン pin。 main 系統で動作確認した版に固定し、
# 非互換更新は意図して上げる(Renovate / dependabot で PR 化想定)。
terraform {
  required_version = ">= 1.7.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
  }

  # 本番は S3 backend(env ごとに DynamoDB ロック付与)。 ここは placeholder。
  # backend "s3" {
  #   bucket         = "inventory-platform-tfstate"
  #   key            = "cognito/terraform.tfstate"
  #   region         = "ap-northeast-1"
  #   dynamodb_table = "inventory-platform-tflock"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.region
}
