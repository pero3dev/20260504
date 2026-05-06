package com.example.inventory.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Analytics Service — 業態系イベント全消費 → テナント × 業態 × 日付の集計テーブルを構築する。
 *
 * <p>本 MVP のスコープ:
 *
 * <ul>
 *   <li>Kafka コンシューマで {@code retail.order.placed.v1} と {@code wholesale.order.placed.v1} を購読
 *   <li>テナント × 業態 × 日付の {@code daily_order_summary} を UPSERT で集計
 *   <li>REST API: 期間指定で集計結果を取得
 * </ul>
 *
 * <p>Phase 2 以降のスコープ(本 MVP では未実装):
 *
 * <ul>
 *   <li>cancel / shipped イベントの反映(注文ライフサイクル全体の集計)
 *   <li>3PL movement / Manufacturing の KPI(製造リードタイム / 出庫トレンド)
 *   <li>BI ツール接続用の OLAP マテリアライズドビュー
 * </ul>
 *
 * <p>マルチテナンシ方式: Pool(共通DB、tenant_id 列、ADR-0003)。
 */
@SpringBootApplication
@EnableKafka
public class AnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApplication.class, args);
    }
}
