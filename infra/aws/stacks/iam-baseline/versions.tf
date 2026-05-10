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
      Stack     = "iam-baseline"
      # iam-baseline は account-level の env 非依存 stack なので Environment tag は付与しない。
    }
  }
}
