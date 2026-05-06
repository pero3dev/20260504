package com.example.inventory.manufacturing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Manufacturing — 業態系の 4 個目。製造業向け。BOM(部品構成)と WorkOrder(製造指図)を管理する。
 *
 * <p>業態固有のポイント:
 *
 * <ul>
 *   <li>BOM は「製品 SKU を作るのに必要な部品 SKU と数量」のマスタ参照
 *   <li>WorkOrder.place で BOM をスナップショットして指図に焼き付け、 release した時点で部品の引当(消費)が必要になる
 * </ul>
 *
 * <p>マルチテナンシ方式: Bridge(tenant_&lt;id&gt; スキーマ毎に分離、ADR-0003)。
 *
 * <p>WorkOrder release 時に {@code manufacturing.work_order.released.v1} を Outbox 経由で発行する。 Inventory
 * Core への部品引当連携は別タスク(D10)で配線する。
 */
@SpringBootApplication
@EnableScheduling
public class ManufacturingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManufacturingApplication.class, args);
    }
}
