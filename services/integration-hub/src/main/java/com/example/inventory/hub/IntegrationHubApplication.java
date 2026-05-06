package com.example.inventory.hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Integration Hub — 業態系 / Inventory イベントを外部システム(EDI / S3 / SFTP / 外部 EC)に届ける橋渡し。
 *
 * <p>本 MVP のスコープ:
 *
 * <ul>
 *   <li>1 つの参照アダプタ実装: {@code retail.order.placed.v1} → CSV ローカルファイル出力
 *   <li>{@code OutboundDestination} ポート抽象 + {@code LocalFileDestination} 実装
 * </ul>
 *
 * <p>Phase 2 以降に追加予定の Adapter:
 *
 * <ul>
 *   <li>S3 アップロード(マニフェスト + Object Lock)
 *   <li>SFTP 送信(取引先サーバ)
 *   <li>AS2 / EDIFACT(Apache Camel + Smooks)
 *   <li>外部 EC API(Shopify / Amazon Seller Central 等)
 *   <li>distribution-BMS(自社実装、CLAUDE.md 参照)
 * </ul>
 *
 * <p>本サービスは DB を持たないステートレス構成(MVP)。冪等性は外部システム側 + Kafka consumer の at-least-once
 * 配信に依存する(同一注文の二重送信は外部側で吸収する想定)。
 */
@SpringBootApplication
@EnableKafka
public class IntegrationHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegrationHubApplication.class, args);
    }
}
