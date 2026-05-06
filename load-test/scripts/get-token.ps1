# identity-broker から負荷試験用 JWT を 2 段階フローで取得する(PowerShell 版)。
#
# 段階:
#   1. POST /v1/auth/sessions       (email + password)         -> sessionToken
#   2. POST /v1/auth/tenant-sessions (sessionToken + tenantId) -> accessToken
#
# 前提:
#   - identity-broker が host で起動中(http://localhost:8081)
#   - 該当 email/password で認証可能なユーザが登録済み
#
# 使い方:
#   $env:TOKEN = .\load-test\scripts\get-token.ps1
#
# 環境変数(全て optional):
#   IDENTITY_BROKER_ISSUER  identity-broker のベース URL(既定 http://localhost:8081)
#   LOAD_TEST_EMAIL         認証 email(既定 loadtest@example.com)
#   LOAD_TEST_PASSWORD      パスワード(既定 loadtest-password)
#   LOAD_TEST_TENANT        対象 tenant id(既定 dev)

$ErrorActionPreference = "Stop"

$Issuer = if ($env:IDENTITY_BROKER_ISSUER) { $env:IDENTITY_BROKER_ISSUER } else { "http://localhost:8081" }
$Email = if ($env:LOAD_TEST_EMAIL) { $env:LOAD_TEST_EMAIL } else { "loadtest@example.com" }
$Password = if ($env:LOAD_TEST_PASSWORD) { $env:LOAD_TEST_PASSWORD } else { "loadtest-password" }
$Tenant = if ($env:LOAD_TEST_TENANT) { $env:LOAD_TEST_TENANT } else { "dev" }

Write-Host "[info] step 1: POST $Issuer/v1/auth/sessions as $Email" -ForegroundColor DarkGray

$sessionBody = @{
    email    = $Email
    password = $Password
} | ConvertTo-Json

$session = Invoke-RestMethod -Method POST -Uri "$Issuer/v1/auth/sessions" `
    -ContentType "application/json" -Body $sessionBody

if (-not $session.sessionToken) {
    throw "Failed to get sessionToken from identity-broker"
}

Write-Host "[info] step 2: POST $Issuer/v1/auth/tenant-sessions for tenant $Tenant" -ForegroundColor DarkGray

$tenantBody = @{
    sessionToken = $session.sessionToken
    tenantId     = $Tenant
} | ConvertTo-Json

$tokenResponse = Invoke-RestMethod -Method POST -Uri "$Issuer/v1/auth/tenant-sessions" `
    -ContentType "application/json" -Body $tenantBody

# accessToken を出力
$tokenResponse.accessToken
