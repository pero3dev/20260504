# ADR-0008: Audit — AOP-based collection with WORM storage and hash chain

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: Architecture, Compliance

## Context

J-SOX compliance requires that every state-changing operation, plus reads of compliance-sensitive data, is logged with the operator's identity and tamper-evident storage retained for one year. The data-volume implication is large — covering even a subset of reads at 10K concurrent users approaches the same order of magnitude as the inventory transaction volume.

## Decision

Collect audit events in the **application layer via AOP**, using an `@Auditable` annotation on every state-changing operation and on reads explicitly classified as compliance-sensitive (cost data, partner data, permission changes, master mutations). Audit events flow through a Kafka topic to a dedicated Audit service, which:

1. Computes a per-record SHA-256 hash chained to the previous record's hash.
2. Once per day, computes a Merkle root over the day's records and writes it to a separate S3 bucket as the anchor.
3. Stores records as Parquet in **S3 with Object Lock in Compliance mode** for one year, partitioned by `tenant_id / yyyy-mm-dd / event_type`.
4. Exposes a query path via Athena. No interactive UI is built — auditors get reports.

## Consequences

**Positive.** Business context (who, with what authority, against what entity) is captured naturally because AOP runs inside the use case. WORM storage in Compliance mode means even an AWS root user cannot delete or modify records before retention expiry. Hash-chain plus Merkle anchor gives us a cryptographic story for J-SOX inspectors.

**Negative.** **AOP-only collection has a coverage gap** — any code path that mutates the database without going through an `@Auditable`-annotated use case is invisible to audit. Mitigations are mandatory and are tracked as compensating controls:

- ArchUnit enforces that every method writing to a `commons-persistence` repository is reachable only through an `@Auditable` use case (no direct mapper-from-controller paths).
- Aurora WAL is exported to a separate locked-down account for forensic reconstruction.
- DBAs get a separate, ticketed channel for direct DB mutations; those mutations are post-fact reconciled to audit by a daily job.
- Code review checklist explicitly asks: "is this state-changing path Auditable?"

**Neutral.** Audit Service is throughput-critical but has no real-time SLA — eventual write to S3 within minutes is fine. Producer-side back-pressure must not block the use case (Kafka producer is fire-and-forget with retries).

## Alternatives considered

### Option 1: CDC from Aurora WAL via Debezium
Captures every DB change, no annotation needed. Rejected as the primary mechanism because CDC has no notion of business context — it cannot say *which user* under *which authority* caused the change. Considered as a future complement (hybrid AOP + CDC) and we may revisit if the AOP coverage gap proves harder to close than expected.

### Option 2: Database-level audit (pgaudit)
Postgres logs every statement. Rejected for the same reason as CDC, plus it ties audit volume to query patterns we cannot control.

### Option 3: Amazon QLDB as primary store
Native ledger semantics. Rejected because QLDB is being de-emphasized in AWS's roadmap and S3 Object Lock + app-side hash chain is the standard pattern for new builds.

## Implementation status (2026-05-06, D3 task)

実装済み(audit-service Java + SQL):

- ✅ AOP 取込: `@Auditable` aspect(`commons-audit`)
- ✅ Kafka 経由でレコード受信: `AuditEventListener`
- ✅ Per-record SHA-256 + prev_hash チェーン: `Sha256HashCalculator` + `ProcessAuditEventService`
- ✅ チェーン整合性検証 UseCase + REST API: `AuditChainVerifier` + `GET /admin/audit-chain/verify`
- ✅ **DB レベル WORM 強制**: V2 マイグレーションで `audit_record` / `audit_merkle_anchor` に対する UPDATE/DELETE をトリガで拒否
- ✅ **日次 Merkle anchor 計算**: `Sha256MerkleTreeCalculator` + `ComputeDailyMerkleAnchorService` + `DailyMerkleAnchorScheduler`(`platform.audit.anchor.enabled=true` で起動)
- ✅ **anchor 整合性検証**: `VerifyMerkleAnchorService` + `GET /admin/audit-chain/anchor/verify`
- ✅ **anchor 手動計算 API**: `POST /admin/audit-chain/anchor`

### 2026-05-07 update — A4 task で WORM 二次保管が完備

S3 投入経路を実装し、 残 4 項目を解消:

- ✅ **S3 Object Lock (Compliance mode) への record 投入**: 日次 `ComputeDailyMerkleAnchorService` が anchor 計算後に同期で S3 PUT(`AuditArchiveExporter` port + `S3AuditArchiveExporter` adapter)
- ✅ **Merkle root の S3 二重保管**: 同じ scheduler tick で `exportAnchor` も実行
- ✅ **Athena 経由のクエリ**: `infra/audit-s3/glue/*.sql` で External Table 定義(partition projection 有効)
- ✅ **1 年保持期限**: bucket 設定で Default retention 365 days + lifecycle 365 days(`infra/audit-s3/bucket-config/object-lock-configuration.json`)

**format に関する補足**:

ADR では Parquet 投入を想定していたが、 MVP は **JSON Lines + gzip**(records)/ JSON(anchor)で実装した。 理由は `parquet-avro` の transitive 依存(hadoop-common 等)が重く、 audit-service の jar サイズと build 時間に大きく響くため。 Athena は JSON も外部表で読めるため J-SOX 上の要件は満たす。 列指向(scan cost 圧縮)が必要になった段階で Parquet 化に差し替える(format は port インターフェイスの内側に閉じている)。

**運用への引継ぎ**:

- S3 export 失敗は warn ログのみで anchor 自体の整合性は失われない設計(`ComputeDailyMerkleAnchorService.exportToArchive`)。 再投入は POST `/admin/audit-chain/anchor` で同一 (tenant, date) を再計算しようとしても DB 側の WORM トリガで弾かれるため、 **再 export だけしたい場合は手動で S3 PUT する運用ジョブが必要**(後フェーズ Future Work)。
- 本番デプロイ手順は `infra/audit-s3/README.md` の Step 1〜10 を参照。

## References

- `memory/business_requirements.md` — audit requirements (Q6)
- `memory/architecture.md` — C1 audit architecture
- `memory/design_implementation.md` — `@Auditable` aspect in `commons-audit`
- `services/audit-service/.../Sha256HashCalculator.java` — チェーンハッシュ計算
- `services/audit-service/.../Sha256MerkleTreeCalculator.java` — Merkle tree 計算(D3)
- `services/audit-service/.../ComputeDailyMerkleAnchorService.java` — 日次 anchor 計算(D3)
- `services/audit-service/.../V2__audit_worm_and_merkle_anchor.sql` — WORM トリガ + anchor テーブル(D3)
