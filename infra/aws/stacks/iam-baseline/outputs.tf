output "github_oidc_provider_arn" {
  description = "GitHub Actions OIDC provider の ARN。 他 stack で role を作る際 trust principal に渡す。"
  value       = aws_iam_openid_connect_provider.github.arn
}

output "tf_deploy_role_arn" {
  description = <<-EOT
    Terraform apply を実行する deploy role の ARN。
    GitHub Actions workflow から `aws-actions/configure-aws-credentials` の
    `role-to-assume` 入力に渡す。
  EOT
  value       = aws_iam_role.tf_deploy.arn
}

output "tf_deploy_role_name" {
  description = "deploy role 名。 monitoring / audit から名前で role を引きたい場合に使う。"
  value       = aws_iam_role.tf_deploy.name
}
