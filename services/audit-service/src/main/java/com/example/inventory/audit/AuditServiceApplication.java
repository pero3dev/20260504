package com.example.inventory.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Audit Service — {@code audit.log.v1} 全消費 → SHA-256 ハッシュチェーン構築 → DB 永続化(ADR-0008)。
 *
 * <p>本 MVP のスコープ:
 *
 * <ul>
 *   <li>Kafka コンシューマで audit.log.v1 を購読(全テナント・全サービス)
 *   <li>テナントごとのチェーン状態を DB で管理(advisory lock + シーケンス採番)
 *   <li>イベント単位で SHA-256(prev_hash + 正規化JSON)を計算して保存
 *   <li>idempotent: event_id UNIQUE 制約による重複検出
 * </ul>
 *
 * <p>Phase 2 で実装する項目(port のみ用意):
 *
 * <ul>
 *   <li>S3 Object Lock(WORM, Compliance Mode)への Parquet 投入
 *   <li>Merkle root の日次アンカリング(別バケット)
 *   <li>Athena 経由のクエリ(REST API は作らない、運用ツール直接)
 *   <li>古いレコードの保持期間 1 年での自動失効(Object Lock 失効と整合)
 * </ul>
 *
 * <p>マルチテナンシ方式は Pool(共通DB、tenant_id 列、ADR-0003)。 本サービス自体は REST 業務 API を持たず、actuator のみ公開する。
 */
@SpringBootApplication
@EnableKafka
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
