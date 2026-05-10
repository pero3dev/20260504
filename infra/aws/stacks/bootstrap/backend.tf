# 初回 apply は local state で実行する (本 stack が S3 バケット自身を作るため)。
# apply 完了後、 下のブロックをコメント解除して `terraform init -migrate-state` で
# 自分自身の state を新規バケットへ移動させる。 詳細手順は本 stack の README.md を参照。
#
# terraform {
#   backend "s3" {
#     bucket         = "inventory-platform-tfstate"
#     key            = "aws/stacks/bootstrap/main.tfstate"
#     region         = "ap-northeast-1"
#     dynamodb_table = "inventory-platform-tflock"
#     encrypt        = true
#     kms_key_id     = "alias/tfstate"
#   }
# }
