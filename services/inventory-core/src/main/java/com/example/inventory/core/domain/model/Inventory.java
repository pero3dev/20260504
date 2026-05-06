package com.example.inventory.core.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.core.domain.event.InventoryReceivedEvent;
import com.example.inventory.core.domain.event.InventoryReleasedEvent;
import com.example.inventory.core.domain.event.InventoryReservedEvent;
import com.example.inventory.core.domain.event.InventoryShippedEvent;

/**
 * Inventory 集約ルート。本プラットフォームにおける 「テナント内の (SKU, Location) に対する利用可能数量・引当数量」の権威 (ADR-0002、ADR-0004)。
 *
 * <p>純粋な POJO。MyBatis/JPA/フレームワークのアノテーションは持たない (永続化は adapter 層の責務、ADR-0009)。 {@code version}
 * フィールドにより楽観ロックを行う。
 */
public final class Inventory {

    private final InventoryId id;
    private final SkuId skuId;
    private final LocationId locationId;
    private Quantity available;
    private Quantity reserved;
    private long version;

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    /** 永続化された状態から集約を再構築する。 */
    public static Inventory restore(
            InventoryId id,
            SkuId skuId,
            LocationId locationId,
            Quantity available,
            Quantity reserved,
            long version) {
        return new Inventory(id, skuId, locationId, available, reserved, version);
    }

    /** 新規の在庫レコードを作成する。{@code id} は呼び出し側(Snowflake)から供給する。 */
    public static Inventory create(
            InventoryId id, SkuId skuId, LocationId locationId, Quantity initial) {
        return new Inventory(id, skuId, locationId, initial, Quantity.ZERO, 0L);
    }

    private Inventory(
            InventoryId id,
            SkuId skuId,
            LocationId locationId,
            Quantity available,
            Quantity reserved,
            long version) {
        this.id = id;
        this.skuId = skuId;
        this.locationId = locationId;
        this.available = available;
        this.reserved = reserved;
        this.version = version;
    }

    /**
     * {@code quantity} 数量を引当てる(available → reserved に移動)。 利用可能数量が要求量未満の場合は {@link
     * InsufficientStockException} を投げる。 成功時は {@link InventoryReservedEvent} を発行する。
     *
     * <p>イベントには post-state(引当後の available/reserved/version)を含める。 リポジトリの save() が version を +1
     * する規約のため、本メソッドは {@code version + 1} を versionAfter として埋める。save 失敗時は pendingEvents が outbox
     * に書かれない(Transactional Outbox 整合性、ADR-0009)。
     */
    public ReservationId reserve(ReservationId reservationId, Quantity quantity) {
        if (!available.isAtLeast(quantity)) {
            throw new InsufficientStockException(id, available, quantity);
        }
        this.available = available.minus(quantity);
        this.reserved = reserved.plus(quantity);
        this.pendingEvents.add(
                new InventoryReservedEvent(
                        id.value(),
                        skuId.value(),
                        locationId.value(),
                        reservationId.value(),
                        quantity.value(),
                        this.available.value(),
                        this.reserved.value(),
                        this.version + 1,
                        java.time.Instant.now()));
        return reservationId;
    }

    /**
     * 入荷により利用可能在庫を増やす。{@code available += quantity}。引当済(reserved)には影響しない。 成功時に {@link
     * InventoryReceivedEvent} を発行する。
     */
    public void receive(Quantity quantity) {
        if (quantity.value() == 0) {
            throw new IllegalArgumentException("受入数量は 1 以上である必要があります");
        }
        this.available = available.plus(quantity);
        this.pendingEvents.add(
                new InventoryReceivedEvent(
                        id.value(),
                        skuId.value(),
                        locationId.value(),
                        quantity.value(),
                        this.available.value(),
                        this.reserved.value(),
                        this.version + 1,
                        java.time.Instant.now()));
    }

    /**
     * 引当済み在庫を available に戻す(注文キャンセル等の補償)。{@code reserved -= quantity}, {@code available +=
     * quantity}。
     *
     * <p>{@code reserved} が要求量未満の場合は {@link InsufficientReservedException} を投げる。 成功時に {@link
     * InventoryReleasedEvent} を発行する。{@link #ship} と異なり available が同量増える(系外に出ない、保留解除)。
     */
    public void release(Quantity quantity) {
        if (quantity.value() == 0) {
            throw new IllegalArgumentException("release 数量は 1 以上である必要があります");
        }
        if (!reserved.isAtLeast(quantity)) {
            throw new InsufficientReservedException(id, reserved, quantity);
        }
        this.reserved = reserved.minus(quantity);
        this.available = available.plus(quantity);
        this.pendingEvents.add(
                new InventoryReleasedEvent(
                        id.value(),
                        skuId.value(),
                        locationId.value(),
                        quantity.value(),
                        this.available.value(),
                        this.reserved.value(),
                        this.version + 1,
                        java.time.Instant.now()));
    }

    /**
     * 引当済み在庫を出荷で消化する。{@code reserved -= quantity}。{@code available} は変わらない (reserve 時に既に available
     * から差し引かれているため)。
     *
     * <p>{@code reserved} が要求量未満の場合は {@link InsufficientReservedException} を投げる。 成功時に {@link
     * InventoryShippedEvent} を発行する。
     */
    public void ship(Quantity quantity) {
        if (quantity.value() == 0) {
            throw new IllegalArgumentException("出荷数量は 1 以上である必要があります");
        }
        if (!reserved.isAtLeast(quantity)) {
            throw new InsufficientReservedException(id, reserved, quantity);
        }
        this.reserved = reserved.minus(quantity);
        this.pendingEvents.add(
                new InventoryShippedEvent(
                        id.value(),
                        skuId.value(),
                        locationId.value(),
                        quantity.value(),
                        this.available.value(),
                        this.reserved.value(),
                        this.version + 1,
                        java.time.Instant.now()));
    }

    public InventoryId id() {
        return id;
    }

    public SkuId skuId() {
        return skuId;
    }

    public LocationId locationId() {
        return locationId;
    }

    public Quantity available() {
        return available;
    }

    public Quantity reserved() {
        return reserved;
    }

    public long version() {
        return version;
    }

    public List<DomainEvent> pendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    public void clearPendingEvents() {
        pendingEvents.clear();
    }
}
