package com.example.inventory.readmodel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Inventory Read Model — Inventory Core が発行する {@code inventory.movement.v1} を購読し、Redis
 * に在庫投影を作る(ADR-0004 CQRS の Read 側)。
 *
 * <p>外部公開 API は {@code GET /v1/inventories/{id}}(OpenAPI 仕様 InventoryApi)。
 */
@SpringBootApplication
@EnableKafka
public class InventoryReadModelApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryReadModelApplication.class, args);
    }
}
