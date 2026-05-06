#!/usr/bin/env bash
# identity-broker から負荷試験用 JWT を 2 段階フローで取得する。
#
# 段階:
#   1. POST /v1/auth/sessions  (email + password)  → sessionToken
#   2. POST /v1/auth/tenant-sessions  (sessionToken + tenantId)  → accessToken
#
# 前提:
#   - identity-broker が host で起動中(http://localhost:8081)
#   - 該当 email/password で認証可能なユーザが登録済み
#   - そのユーザが指定 tenantId へのアクセス権を持つ
#
# 使い方:
#   TOKEN=$(./load-test/scripts/get-token.sh)
#
# 環境変数(全て optional):
#   IDENTITY_BROKER_ISSUER  identity-broker のベース URL(既定 http://localhost:8081)
#   LOAD_TEST_EMAIL         認証 email(既定 loadtest@example.com)
#   LOAD_TEST_PASSWORD      パスワード(既定 loadtest-password)
#   LOAD_TEST_TENANT        対象 tenant id(既定 dev)

set -euo pipefail

ISSUER="${IDENTITY_BROKER_ISSUER:-http://localhost:8081}"
EMAIL="${LOAD_TEST_EMAIL:-loadtest@example.com}"
PASSWORD="${LOAD_TEST_PASSWORD:-loadtest-password}"
TENANT="${LOAD_TEST_TENANT:-dev}"

echo "[info] step 1: POST ${ISSUER}/v1/auth/sessions as ${EMAIL}" >&2

session_response=$(curl -fsS -X POST "${ISSUER}/v1/auth/sessions" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}")

if command -v jq >/dev/null 2>&1; then
    session_token=$(echo "$session_response" | jq -r .sessionToken)
else
    session_token=$(echo "$session_response" | grep -oP '"sessionToken"\s*:\s*"\K[^"]+')
fi

if [ -z "$session_token" ] || [ "$session_token" = "null" ]; then
    echo "[error] failed to get sessionToken; response was:" >&2
    echo "$session_response" >&2
    exit 1
fi

echo "[info] step 2: POST ${ISSUER}/v1/auth/tenant-sessions for tenant ${TENANT}" >&2

token_response=$(curl -fsS -X POST "${ISSUER}/v1/auth/tenant-sessions" \
    -H "Content-Type: application/json" \
    -d "{\"sessionToken\":\"${session_token}\",\"tenantId\":\"${TENANT}\"}")

if command -v jq >/dev/null 2>&1; then
    echo "$token_response" | jq -r .accessToken
else
    echo "$token_response" | grep -oP '"accessToken"\s*:\s*"\K[^"]+'
fi
