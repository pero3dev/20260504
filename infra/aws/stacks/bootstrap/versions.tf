# Terraform / provider バージョン pin。 全 stack で同じ pin を使う。
# 非互換更新は意図して上げる(Renovate / dependabot で PR 化想定)。
terraform {
  required_version = ">= 1.7.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "inventory-platform"
      ManagedBy = "terraform"
      Stack     = "bootstrap"
      # Environment / CostCenter は本 stack 非依存 (env 横断の共有インフラ) なので付与しない。
      # 各 env stack 側で env-specific tag を default_tags で追加する。
    }
  }
}
