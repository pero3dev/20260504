package com.example.inventory.master;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Master Data — SKU / 拠点 / 取引先マスタの権威(ADR-0002)。
 *
 * <p>本 MVP のスコープ: <b>SKU</b> のみ Create / Get。Location と Partner は次イテレーション。
 *
 * <p>マスタ変更は {@code master.product.v1} などのトピックへ Outbox 経由で発行され、 業態系サービス・Inventory Read
 * Model・Integration Hub が購読する(ADR-0002)。
 *
 * <p>マルチテナンシ方式: Bridge(tenant_&lt;id&gt; スキーマ毎に分離、ADR-0003)。
 */
@SpringBootApplication
@EnableScheduling
public class MasterDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(MasterDataApplication.class, args);
    }
}
