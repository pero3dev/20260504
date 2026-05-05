package com.example.inventory.tpl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 3PL (Third-Party Logistics) — 業態系の 2 個目。委託元(Partner)から預かった商品を 自社倉庫で保管・出荷する事業者向けサービス。
 *
 * <p>マルチテナンシ方式: Bridge(tenant_&lt;id&gt; スキーマ毎に分離、ADR-0003)。 同じ会社内の異なる事業部・支店等を tenant として分けるケースを想定。
 *
 * <p>主な集約は {@code StockMovement}(入出庫)。Order ベースの Retail/EC とは異なり、 注文(顧客)を持たず、Partner
 * ごとの入出庫履歴と保管状態が中心 (倉庫業のドメイン)。
 *
 * <p>確定時に {@code tpl.stock.movement.v1} を Outbox 経由で発行し、Inventory Core が 在庫増減を反映する(将来連携、本 MVP
 * は発行のみ)。
 */
@SpringBootApplication
@EnableScheduling
public class TplApplication {

    public static void main(String[] args) {
        SpringApplication.run(TplApplication.class, args);
    }
}
