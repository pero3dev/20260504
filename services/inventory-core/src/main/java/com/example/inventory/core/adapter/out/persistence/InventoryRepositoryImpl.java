package com.example.inventory.core.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.commons.event.DomainEventPublisher;
import com.example.inventory.commons.persistence.OptimisticLockSupport;
import com.example.inventory.core.application.port.out.InventoryRepository;
import com.example.inventory.core.domain.model.Inventory;
import com.example.inventory.core.domain.model.InventoryId;
import com.example.inventory.core.domain.model.LocationId;
import com.example.inventory.core.domain.model.Quantity;
import com.example.inventory.core.domain.model.SkuId;

/**
 * Inventory リポジトリの MyBatis ベース実装(ADR-0009)。
 *
 * <p>{@code save} はバージョン付き UPDATE を実行し、集約に積まれた未発行の ドメインイベントを {@link DomainEventPublisher} 経由で
 * outbox に追記する (Transactional Outbox)。同一トランザクション内で行われるため、 集約の永続化と outbox 書込は原子的。
 *
 * <p>テナントの {@code search_path} は commons-tenant の MyBatis インターセプタが設定する。
 */
@Repository
public class InventoryRepositoryImpl implements InventoryRepository {

    private final InventoryMapper inventoryMapper;
    private final DomainEventPublisher eventPublisher;

    public InventoryRepositoryImpl(
            InventoryMapper inventoryMapper, DomainEventPublisher eventPublisher) {
        this.inventoryMapper = inventoryMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<Inventory> findById(InventoryId id) {
        InventoryRow row = inventoryMapper.findById(id.value());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(
                Inventory.restore(
                        new InventoryId(row.id()),
                        new SkuId(row.skuId()),
                        new LocationId(row.locationId()),
                        new Quantity(row.available()),
                        new Quantity(row.reserved()),
                        row.version()));
    }

    @Override
    public Inventory save(Inventory aggregate) {
        InventoryRow row =
                new InventoryRow(
                        aggregate.id().value(),
                        aggregate.skuId().value(),
                        aggregate.locationId().value(),
                        aggregate.available().value(),
                        aggregate.reserved().value(),
                        aggregate.version() + 1);

        if (aggregate.version() == 0L) {
            inventoryMapper.insert(row);
        } else {
            int affected = inventoryMapper.update(row, aggregate.version());
            OptimisticLockSupport.verify(
                    affected, "Inventory", aggregate.id().value(), aggregate.version());
        }

        for (DomainEvent event : aggregate.pendingEvents()) {
            eventPublisher.publish(event);
        }
        aggregate.clearPendingEvents();
        return aggregate;
    }

    @Override
    public void delete(Inventory aggregate) {
        int affected = inventoryMapper.delete(aggregate.id().value(), aggregate.version());
        OptimisticLockSupport.verify(
                affected, "Inventory", aggregate.id().value(), aggregate.version());
    }
}
