-- ADR-0008 A4: Athena 経由で audit-records を読むための External Table 定義。
-- partition は tenant_id × date(JSON Lines + gzip フォーマット)。
-- 実行は Glue Console / Athena Query Editor / aws athena start-query-execution のいずれか。
-- DATABASE / LOCATION / partition は本番 AWS account で実値に置換すること。

CREATE EXTERNAL TABLE IF NOT EXISTS audit_db.audit_records (
    tenantId          string,
    sequence          bigint,
    eventId           bigint,
    action            string,
    targetType        string,
    targetId          string,
    operatorUserId    string,
    operatorTenantId  string,
    outcome           string,
    errorCode         string,
    readOnly          boolean,
    payloadJson       string,
    occurredAt        string,
    prevHash          string,
    hash              string
)
PARTITIONED BY (
    tenant string,
    date   string
)
ROW FORMAT SERDE 'org.openx.data.jsonserde.JsonSerDe'
WITH SERDEPROPERTIES (
    'ignore.malformed.json' = 'true',
    'dots.in.keys' = 'false',
    'case.insensitive' = 'true'
)
LOCATION 's3://PLACEHOLDER_AUDIT_BUCKET/audit-records/'
TBLPROPERTIES (
    'has_encrypted_data' = 'false',
    'projection.enabled' = 'true',
    'projection.tenant.type' = 'injected',
    'projection.date.type' = 'date',
    'projection.date.range' = '2026-01-01,NOW',
    'projection.date.format' = 'yyyy-MM-dd',
    'projection.date.interval' = '1',
    'projection.date.interval.unit' = 'DAYS',
    'storage.location.template' = 's3://PLACEHOLDER_AUDIT_BUCKET/audit-records/tenant=${tenant}/date=${date}/'
);
