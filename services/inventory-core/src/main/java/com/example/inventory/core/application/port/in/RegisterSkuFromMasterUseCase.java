package com.example.inventory.core.application.port.in;

import java.time.Instant;

/**
 * Master Data の {@code master.product.v1} を受信して投影テーブルに反映するユースケース。
 * 入力はトピックのペイロードと同型のフラットなコマンドにすることで、Kafka アダプタを薄く保つ。
 */
public interface RegisterSkuFromMasterUseCase {

    /**
     * @param aggregateId Master Data 側の集約 ID(Snowflake)
     * @param code SKU 自然キー
     * @param name 表示名
     * @param unitOfMeasure 単位
     * @param versionAfter イベント発行時点の version。再配信時の比較に用いる
     * @param occurredAt イベント発生時刻
     */
    record Command(
            long aggregateId,
            String code,
            String name,
            String unitOfMeasure,
            long versionAfter,
            Instant occurredAt) {

        public Command {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("code は必須");
            }
            if (versionAfter < 1) {
                throw new IllegalArgumentException("versionAfter は 1 以上");
            }
        }
    }

    void register(Command command);
}
