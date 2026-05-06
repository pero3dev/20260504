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

未実装(本 ADR 対象だがインフラ寄り、別タスク):

- ⏳ S3 Object Lock (Compliance mode) への Parquet 投入(現状は audit_record に DB 保管のみ)
- ⏳ Merkle root の S3 への二重保管(現状は audit_merkle_anchor テーブルのみ)
- ⏳ Athena 経由のクエリ
- ⏳ 1 年保持期限による自動失効

## References

- `memory/business_requirements.md` — audit requirements (Q6)
- `memory/architecture.md` — C1 audit architecture
- `memory/design_implementation.md` — `@Auditable` aspect in `commons-audit`
- `services/audit-service/.../Sha256HashCalculator.java` — チェーンハッシュ計算
- `services/audit-service/.../Sha256MerkleTreeCalculator.java` — Merkle tree 計算(D3)
- `services/audit-service/.../ComputeDailyMerkleAnchorService.java` — 日次 anchor 計算(D3)
- `services/audit-service/.../V2__audit_worm_and_merkle_anchor.sql` — WORM トリガ + anchor テーブル(D3)
