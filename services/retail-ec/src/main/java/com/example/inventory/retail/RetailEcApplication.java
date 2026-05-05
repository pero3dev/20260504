package com.example.inventory.retail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Retail/EC — 業態系の 1 個目。注文の受付・状態管理(ADR-0002)。
 *
 * <p>マルチテナンシ方式: Bridge(tenant_&lt;id&gt; スキーマ毎に分離、ADR-0003)。
 *
 * <p>本サービスは「注文の権威」。Order を確定したら {@code retail.order.placed.v1} を Outbox 経由で発行し、 Inventory
 * Core(在庫引当)・Notification(顧客通知)・Integration Hub(EC 連携)等が下流で処理する。
 *
 * <p>Inventory Core への直接同期呼出(REST)は次イテレーション。MVP は Outbox 発行のみ。
 */
@SpringBootApplication
@EnableScheduling
public class RetailEcApplication {

    public static void main(String[] args) {
        SpringApplication.run(RetailEcApplication.class, args);
    }
}
