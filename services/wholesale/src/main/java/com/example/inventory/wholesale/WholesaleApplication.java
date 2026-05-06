package com.example.inventory.wholesale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wholesale — 業態系の 3 個目。法人取引先(Partner)向け大口受注(ADR-0002)。
 *
 * <p>Retail/EC との対比:
 *
 * <ul>
 *   <li>Retail/EC: 顧客(エンドユーザ)が SKU を購入。価格は SKU マスタ標準
 *   <li>Wholesale: 法人取引先が大口で発注。価格は <strong>取引先別契約価格(PartnerPrice)</strong> で決まる
 * </ul>
 *
 * <p>マルチテナンシ方式: Bridge(tenant_&lt;id&gt; スキーマ毎に分離、ADR-0003)。
 *
 * <p>SalesOrder を確定したら {@code wholesale.order.placed.v1} を Outbox 経由で発行する。 Inventory Core
 * への引当連携は別タスク(D9)で配線する。
 */
@SpringBootApplication
@EnableScheduling
public class WholesaleApplication {

    public static void main(String[] args) {
        SpringApplication.run(WholesaleApplication.class, args);
    }
}
