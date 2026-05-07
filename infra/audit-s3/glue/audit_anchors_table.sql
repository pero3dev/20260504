-- ADR-0008 A4: Athena 経由で audit-anchors を読むための External Table。
-- 1 file = 1 anchor(JSON 単発)。 record と違って partition の date は anchor の対象日。

CREATE EXTERNAL TABLE IF NOT EXISTS audit_db.audit_anchors (
    tenantId       string,
    anchorDate     string,
    rootHash       string,
    recordCount    bigint,
    firstSequence  bigint,
    lastSequence   bigint,
    computedAt     string
)
PARTITIONED BY (
    tenant string,
    date   string
)
ROW FORMAT SERDE 'org.openx.data.jsonserde.JsonSerDe'
WITH SERDEPROPERTIES (
    'ignore.malformed.json' = 'true',
    'case.insensitive' = 'true'
)
LOCATION 's3://PLACEHOLDER_AUDIT_BUCKET/audit-anchors/'
TBLPROPERTIES (
    'projection.enabled' = 'true',
    'projection.tenant.type' = 'injected',
    'projection.date.type' = 'date',
    'projection.date.range' = '2026-01-01,NOW',
    'projection.date.format' = 'yyyy-MM-dd',
    'projection.date.interval' = '1',
    'projection.date.interval.unit' = 'DAYS',
    'storage.location.template' = 's3://PLACEHOLDER_AUDIT_BUCKET/audit-anchors/tenant=${tenant}/date=${date}/'
);
