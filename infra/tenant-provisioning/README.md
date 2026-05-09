## Multi-tenant provisioning ランブック(A5、 ADR-0003 follow-up)

新規テナントを onboard する際の broker-side + Bridge DB schema 作成 + Pool DB 設定の運用手順。

### 構成

| 領域 | 方式 | provision タイミング |
|---|---|---|
| identity-broker | Pool(`tenants` テーブル登録) | 本ランブック Step 1 |
| inventory-core | Bridge(per-tenant schema 作成) | 本ランブック Step 2 |
| master-data | Bridge | 本ランブック Step 2 |
| retail-ec / wholesale / 3pl / manufacturing | Bridge | 本ランブック Step 2 |
| inventory-read-model(Redis) | Pool(key prefix `tenant:<id>:`) | 自動(初回 write 時) |
| audit-service | Pool(`tenant_id` 列 + RLS) | 自動(初回 write 時) |
| notification | Pool(`tenant_id` 列) | 自動(初回 write 時) |
| workflow | Pool(`tenant_id` 列) | 自動(初回 write 時) |
| analytics | Pool(`tenant_id` 列) | 自動(初回 write 時) |
| integration-hub | Pool(`tenant_id` 列、 後で adapter 追加で per-tenant config 必要) | 自動 + adapter ごと |

Bridge 系は schema を物理的に作る必要があり、 Pool 系は不要(`tenant_id` 列で論理分離)。

### Step 1. identity-broker に tenant 登録

```bash
# SUPER_ADMIN role の JWT を取得しておく(production では SecurityConfig で必須化、 MVP では permitAll)
TOKEN="..."
BROKER="https://idp.example.com"

curl -X POST "$BROKER/v1/admin/tenants" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "acme",
    "displayName": "Acme Corporation"
  }'
# 201 Created + TenantResource を期待。 既存なら 409。
```

`tenantId` は `^[a-z0-9][a-z0-9-]{2,31}$`(domain-safe、 schema name にも使用)。

### Step 2. Bridge 系 DB に schema を作成 + Flyway を実行

各 Bridge サービスごとに schema を新規作成し、 Flyway を **その schema に対して** 実行する。 シェルスクリプト雛形:

```bash
#!/usr/bin/env bash
set -euo pipefail
TENANT_ID="${1:?tenant id required}"
SCHEMA="tenant_${TENANT_ID//-/_}"
ADMIN_USER="${ADMIN_USER:-postgres}"
ADMIN_PASS="${ADMIN_PASS:-...}"

# Bridge 系サービスを列挙(各サービスの DB 接続情報は env で別管理)
for service in inventory-core master-data retail-ec wholesale tpl manufacturing; do
  HOST="${service^^}_DB_HOST"
  PORT="${service^^}_DB_PORT"
  DB="${service^^}_DB_NAME"
  echo "=== provisioning $service for $TENANT_ID (schema=$SCHEMA) ==="

  # schema 作成 + アプリ user に GRANT
  PGPASSWORD="$ADMIN_PASS" psql -h "${!HOST}" -p "${!PORT}" -U "$ADMIN_USER" -d "${!DB}" <<SQL
CREATE SCHEMA IF NOT EXISTS "$SCHEMA";
GRANT USAGE  ON SCHEMA "$SCHEMA" TO ${service//-/_}_app;
GRANT CREATE ON SCHEMA "$SCHEMA" TO ${service//-/_}_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA "$SCHEMA" TO ${service//-/_}_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA "$SCHEMA"
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${service//-/_}_app;
SQL

  # Flyway を新 schema に対して実行(各サービスの jar に同梱)
  java -jar /opt/migrations/${service}-migrations.jar \
    -url=jdbc:postgresql://${!HOST}:${!PORT}/${!DB} \
    -user=$ADMIN_USER -password=$ADMIN_PASS \
    -schemas="$SCHEMA" \
    -defaultSchema="$SCHEMA" \
    migrate
done
echo "provisioning 完了 tenant=$TENANT_ID"
```

注意:
- Bridge 系のアプリ user は `<service>_app`(各サービスの `application.yml` 参照)
- `-schemas` は Flyway の **検索対象**、 `-defaultSchema` は **マイグレーションテーブル(`flyway_schema_history`)を置く先**
- Flyway を CLI ではなくマイグレーション専用 Job(K8s)で実行する場合は同等の env 設定で起動

### Step 3. Pool 系の追加処理(必要なら)

- audit-service / workflow / notification / analytics は `tenant_id` 列で論理分離し、 RLS 設定があるサービスは新規 tenant の RLS policy を確認(現状全サービスで policy は user 単位、 tenant 単位の追加は不要)
- integration-hub の adapter で per-tenant 設定が必要なら(SFTP credential 等)、 adapter 固有 secret を ExternalSecretsOperator 経由で投入

### Step 4. Cognito User Pool group(SAML 連携時)

将来 SAML 連携が入ったら、 各 tenant ごとに Cognito group を切る(`acme-users` 等)。 MVP では identity-broker のローカル auth のみなので不要。

### Step 5. 動作確認

```bash
# tenant が登録されたことを確認
curl "$BROKER/v1/admin/tenants/acme" -H "Authorization: Bearer $TOKEN"

# user 1 名を作って membership を付与(現状 SQL 経由、 admin API 化は次フェーズ)
psql ... <<SQL
INSERT INTO users (id, email, password_hash, display_name)
VALUES (snowflake_id_next(), 'admin@acme.test', '\$2a\$10\$...', 'Acme Admin');

INSERT INTO tenant_memberships (id, user_id, tenant_id, tenant_display_name, roles_json)
VALUES (
  snowflake_id_next(),
  (SELECT id FROM users WHERE email='admin@acme.test'),
  'acme',
  'Acme Corporation',
  '["TENANT_ADMIN"]'::jsonb
);
SQL

# login 経路で session token + tenant-session token が取得できることを確認
curl -X POST "$BROKER/v1/auth/sessions" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@acme.test","password":"..."}'
```

### Tenant deactivation

```bash
curl -X POST "$BROKER/v1/admin/tenants/acme/deactivate" \
  -H "Authorization: Bearer $TOKEN"
# 200 + status: DEACTIVATED
# Existing sessions は TTL 切れまで有効。 新規 tenant-session の発行は失敗する想定
# (現状 SelectTenantService で status を見ていない、 Future Work)。
```

DB 側の schema 削除は **絶対に行わない**(audit / 監査要件で履歴保全)。 必要であれば AWS account 取り消し時に S3 export + bucket 削除を運用ジョブで対応。

### SUPER_ADMIN 初回 provisioning(A5 follow-up⁶)

`/v1/admin/**` は SUPER_ADMIN role 必須 (follow-up⁴) のため、 初回はどう SUPER_ADMIN を生成するかの chicken-and-egg 問題が起きる。 解決策:

1. **`platform` テナント** は `V4__platform_tenant.sql` で seed 済(deactivate 不可、 `TenantManagementService` でガード)。 ops は何もしなくて良い
2. **初回 SUPER_ADMIN ユーザを ops が直接 SQL で投入**(identity-broker DB へ psql 等で接続):

   ```sql
   -- 1) BCrypt ハッシュを別ツールで生成しておく(例: htpasswd -bnBC 10 "" "<password>")
   -- 2) Snowflake ID は仮値(production は別途採番ツール)。 安全のため十分大きい値を使う
   INSERT INTO users (id, email, password_hash, display_name, version)
   VALUES (
       1,
       'admin@example.com',
       '$2y$10$abcdef…',  -- BCrypt 60 文字
       'Initial Platform Admin',
       0
   );

   -- 3) platform テナント membership に SUPER_ADMIN role を持たせる
   INSERT INTO tenant_memberships (
       id, user_id, tenant_id, tenant_display_name,
       roles_json, location_scopes_json, partner_scopes_json, tenant_locale
   )
   VALUES (
       2, 1, 'platform', 'Platform Administration',
       '["SUPER_ADMIN"]'::jsonb, '[]'::jsonb, '[]'::jsonb, 'ja'
   );
   ```

3. **動作確認**: `POST /v1/auth/sessions` で email + password → session token → `POST /v1/auth/tenant-sessions` body=`{"tenantId":"platform"}` → access token (roles=`["SUPER_ADMIN"]`) を取得 → `GET /v1/admin/users` 等が叩ける

**federation 経路の SUPER_ADMIN provisioning** は別 phase。 SAML JIT で SUPER_ADMIN role を JIT 付与するには `FederationJitProperties.defaultRole` が VIEWER 固定なため、 group → role mapping 設計が必要。

### セキュリティ要件(production)

- `/v1/admin/**` を JWT 必須 + `SUPER_ADMIN` role に絞る — `SecurityConfig` の `adminFilterChain` で実装済(A5 follow-up⁴)。 SUPER_ADMIN role の provisioning は プラットフォーム管理用テナント membership 経由(運用 SQL)で行う
- `SUPER_ADMIN` role は IAM 連携 admin 経路で発行された JWT のみが持つ(通常の login 経路では発行されない)
- ALB 側で source IP allowlist(社内 VPN / Bastion からのみ)
- audit-service が `/v1/admin/tenants/*` の呼出を全て記録(`TenantManagementService` の register / deactivate / get / listAll に `@Auditable` 付与済み。 read-only な get / listAll も `read = true` で監査)

### Future Work

- Bridge 系 schema 自動 provisioning Job(K8s CronJob で identity-broker の `tenants` テーブル → 各 Bridge DB の schema 整合性チェック + 不足分の自動作成)
- ~~`SelectTenantService` で DEACTIVATED tenant の弾き出し~~ → 実装済(`SelectTenantService.selectTenant` で membership 確認後に `TenantRepository.findById` で status を見て、 `DEACTIVATED` なら `TenantAccessDeniedException` で拒否。 既発行 access token は TTL 切れまで有効、 stateless JWT を per-tenant revocation するには別 mechanism が必要)
- user 管理 admin API(read 系 `GET /v1/admin/users`, `GET /v1/admin/users/{userId}` は実装済、 `UserManagementService` に `@Auditable read=true` 付与で参照行為も監査。 write 系 (register / deactivate / link membership) は password vs. federation-only provisioning の ADR 待ちで次 phase)
- Cognito SAML 連携時の group 自動作成
