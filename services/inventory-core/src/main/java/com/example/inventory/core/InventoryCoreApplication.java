package com.example.inventory.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Inventory Core — 在庫状態に対する唯一の書込権威(ADR-0002、ADR-0004)。
 *
 * <p>在庫イベントは Transactional Outbox(ADR-0009)経由で {@code inventory.movement.v1} トピックへ発行する。
 * 参照系のトラフィックは別サービスである Inventory Read Model が担う。
 */
@SpringBootApplication
@EnableScheduling
public class InventoryCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryCoreApplication.class, args);
    }
}
