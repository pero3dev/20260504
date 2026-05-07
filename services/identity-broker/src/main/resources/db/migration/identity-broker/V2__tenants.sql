-- A5: Tenant lifecycle 管理(ADR-0003 follow-up)。
--
-- identity-broker は Pool 方式の SoR としてテナントの登録/廃止を管理する。
-- business DB(Bridge 方式)側の schema 作成は infra/tenant-provisioning/ の runbook で
-- 別経路で実行される(本ブローカは broker-side のメタデータのみ owns する)。

CREATE TABLE tenants (
    tenant_id        VARCHAR(32)  NOT NULL PRIMARY KEY
        CHECK (tenant_id ~ '^[a-z0-9][a-z0-9-]{2,31}$'),
    display_name     VARCHAR(128) NOT NULL,
    status           VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'DEACTIVATED')),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deactivated_at   TIMESTAMPTZ
);

CREATE INDEX tenants_status_idx ON tenants (status);
