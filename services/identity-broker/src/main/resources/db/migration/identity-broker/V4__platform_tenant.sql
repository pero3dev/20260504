-- A5 follow-up⁶: SUPER_ADMIN provisioning 用の予約テナント seed。
--
-- /v1/admin/** が SUPER_ADMIN role 必須(follow-up⁴)になった時点で、
-- 「初回 SUPER_ADMIN をどう生成するか」が chicken-and-egg になる。
-- 解決策:
--   1. ここで `platform` テナントを seed(deactivate 禁止、 application 層でガード)
--   2. ops が初回起動後に直接 SQL で users + tenant_memberships に
--      SUPER_ADMIN role を含む行を入れる(infra/tenant-provisioning/README.md 参照)
--   3. 当該 user の access token を `selectTenant("platform")` で発行 → admin API が叩ける
--
-- locale は ja(コメント / UI 既定。 admin operator は社内日本語ユーザを想定)。
INSERT INTO tenants (tenant_id, display_name, status, locale)
VALUES ('platform', 'Platform Administration', 'ACTIVE', 'ja')
ON CONFLICT (tenant_id) DO NOTHING;
