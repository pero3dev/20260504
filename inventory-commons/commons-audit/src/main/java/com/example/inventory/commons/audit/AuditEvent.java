package com.example.inventory.commons.audit;

import java.time.Instant;

import com.example.inventory.commons.event.DomainEvent;

/**
 * 監査イベント。Outbox 経由で {@code audit.log.v1} トピックへ発行される(ADR-0008)。
 *
 * <p>Audit Service がこのイベントを購読し、SHA-256 ハッシュチェーンを生成して S3 Object Lock(WORM) に Parquet で書き込む。
 *
 * <p>{@code aggregateId} は DomainEvent インタフェースの要請で long を返す。 targetId が数値表現できるなら parse、できなければ 0
 * を返す。 表示用の正規な targetId は {@link #targetId} 文字列フィールドで保持する。
 */
public record AuditEvent(
        /** 安定したアクションコード(例 {@code INVENTORY_RESERVE})。 */
        String action,
        /** 対象エンティティ種別(例 {@code Inventory})。 */
        String targetType,
        /** 対象ID(文字列)。AuditableAspect が SpEL で評価した結果。 */
        String targetId,
        /** 操作者ID(JWT subject)。未認証は {@code "anonymous"}。 */
        String operatorUserId,
        /** 操作時のテナントID。 */
        String operatorTenantId,
        /** 結末。 */
        AuditOutcome outcome,
        /** 失敗時のエラーコード(成功時は null)。 */
        String errorCode,
        /** 統制上重要な「参照」操作なら true、状態変更なら false。 */
        boolean read,
        /** メソッド第1引数のJSON表現(コマンド/クエリの内容)。サイズ大なら切り詰める。 */
        String inputJson,
        /** 操作開始時刻。 */
        Instant occurredAt)
        implements DomainEvent {

    public static final String TOPIC = "audit.log.v1";
    public static final String SCHEMA_VERSION = "1.0";

    /** inputJson のサイズ上限。これを超えたら切り詰める(audit ストアの肥大化を防ぐ)。 */
    public static final int INPUT_JSON_LIMIT = 4096;

    @Override
    public long aggregateId() {
        if (targetId == null || targetId.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(targetId);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public String schemaVersion() {
        return SCHEMA_VERSION;
    }
}
