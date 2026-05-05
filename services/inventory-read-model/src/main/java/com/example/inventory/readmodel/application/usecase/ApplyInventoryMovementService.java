package com.example.inventory.readmodel.application.usecase;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.inventory.readmodel.application.port.out.InventoryProjectionStore;
import com.example.inventory.readmodel.domain.model.InventoryProjection;

/**
 * Kafka コンシューマから渡される movement イベントを投影に適用する。
 *
 * <p>イベントは post-state(availableAfter / reservedAfter / versionAfter)を持つため、 投影は単一イベントで完全再構築できる。冪等性は
 * version で判定: 既存投影の version 以下のイベントは無視する。
 *
 * <p>本サービスはアプリケーション層に位置するが、@Transactional は不要(Redis への 単一 SET は原子的)。Kafka リスナ側の手動 ack(commits
 * offset)で再配信に対する exactly-once-effect を担保する。
 */
@Service
public class ApplyInventoryMovementService {

    private static final Logger LOG = LoggerFactory.getLogger(ApplyInventoryMovementService.class);

    private final InventoryProjectionStore store;

    public ApplyInventoryMovementService(InventoryProjectionStore store) {
        this.store = store;
    }

    public void applyReserved(
            long inventoryId,
            String skuId,
            String locationId,
            int availableAfter,
            int reservedAfter,
            long versionAfter,
            Instant occurredAt) {
        store.findById(inventoryId)
                .ifPresentOrElse(
                        existing -> {
                            if (existing.isStaleAgainst(versionAfter)) {
                                LOG.debug(
                                        "古いイベントを無視 inventoryId={} 既存version={} 受信version={}",
                                        inventoryId,
                                        existing.version(),
                                        versionAfter);
                                return;
                            }
                            store.save(
                                    new InventoryProjection(
                                            inventoryId,
                                            skuId,
                                            locationId,
                                            availableAfter,
                                            reservedAfter,
                                            versionAfter,
                                            occurredAt));
                        },
                        () ->
                                store.save(
                                        new InventoryProjection(
                                                inventoryId,
                                                skuId,
                                                locationId,
                                                availableAfter,
                                                reservedAfter,
                                                versionAfter,
                                                occurredAt)));
    }
}
