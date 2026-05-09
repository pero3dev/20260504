-- ADR-0022 phase 5a: テナント運用言語(locale)を tenants と tenant_memberships に追加。
--
-- ja / en / ja-JP 等を BCP47 風の最小構成で受ける(本 phase は ja / en のみ正式対応、
-- 他言語は CHECK 緩めで通すが catalog が無いため UI は fallback 表示になる)。
-- IB は access token claim に locale を出し、 BFF が抽出して web app に降ろす。

ALTER TABLE tenants
    ADD COLUMN locale VARCHAR(8) NOT NULL DEFAULT 'ja'
        CHECK (locale ~ '^[a-z]{2}(-[A-Z]{2})?$');

-- tenant_memberships は tenant_display_name を denormalize しているのと同じ理由で
-- tenant_locale を denormalize する(JWT 発行時の hot path で 1 row fetch、
-- JOIN を避け同 query で取れるように)。 tenants.locale が SoR で、
-- 反映は infra/tenant-provisioning/ runbook 経由で別 update SQL を流す想定。
ALTER TABLE tenant_memberships
    ADD COLUMN tenant_locale VARCHAR(8) NOT NULL DEFAULT 'ja';
