package com.example.inventory.tpl.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.tpl.domain.event.StockMovementPlannedEvent;

/**
 * 入出庫集約ルート。委託元(Partner)から預かった商品の自社倉庫における入出庫 1 件を表す。
 *
 * <p>純粋な POJO(ADR-0009)。MyBatis/JPA のアノテーション無し。 楽観ロック用 {@code version} を持ち、save 時は +1 される
 * (commons-persistence 規約)。
 */
public final class StockMovement {

    private final StockMovementId id;
    private final StockMovementCode code;
    private final String partnerCode;
    private final String skuCode;
    private final String locationId;
    private final MovementType movementType;
    private final int quantity;
    private MovementStatus status;
    private final String referenceCode;
    private long version;
    private final Instant plannedAt;

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    public static StockMovement restore(
            StockMovementId id,
            StockMovementCode code,
            String partnerCode,
            String skuCode,
            String locationId,
            MovementType movementType,
            int quantity,
            MovementStatus status,
            String referenceCode,
            long version,
            Instant plannedAt) {
        return new StockMovement(
                id,
                code,
                partnerCode,
                skuCode,
                locationId,
                movementType,
                quantity,
                status,
                referenceCode,
                version,
                plannedAt);
    }

    /** 入出庫予定を計上する({@link StockMovementPlannedEvent} を発行)。 */
    public static StockMovement plan(
            StockMovementId id,
            StockMovementCode code,
            String partnerCode,
            String skuCode,
            String locationId,
            MovementType movementType,
            int quantity,
            String referenceCode) {
        StockMovement m =
                new StockMovement(
                        id,
                        code,
                        partnerCode,
                        skuCode,
                        locationId,
                        movementType,
                        quantity,
                        MovementStatus.PLANNED,
                        referenceCode,
                        0L,
                        Instant.now());
        m.pendingEvents.add(
                new StockMovementPlannedEvent(
                        id.value(),
                        code.value(),
                        partnerCode,
                        skuCode,
                        locationId,
                        movementType.name(),
                        quantity,
                        m.referenceCode,
                        m.version + 1,
                        m.plannedAt));
        return m;
    }

    private StockMovement(
            StockMovementId id,
            StockMovementCode code,
            String partnerCode,
            String skuCode,
            String locationId,
            MovementType movementType,
            int quantity,
            MovementStatus status,
            String referenceCode,
            long version,
            Instant plannedAt) {
        if (partnerCode == null || partnerCode.isBlank())
            throw new IllegalArgumentException("partnerCode は必須");
        if (skuCode == null || skuCode.isBlank()) throw new IllegalArgumentException("skuCode は必須");
        if (locationId == null || locationId.isBlank())
            throw new IllegalArgumentException("locationId は必須");
        if (movementType == null) throw new IllegalArgumentException("movementType は必須");
        if (quantity <= 0) throw new IllegalArgumentException("quantity は正の値");
        if (status == null) throw new IllegalArgumentException("status は必須");
        this.id = id;
        this.code = code;
        this.partnerCode = partnerCode;
        this.skuCode = skuCode;
        this.locationId = locationId;
        this.movementType = movementType;
        this.quantity = quantity;
        this.status = status;
        this.referenceCode = referenceCode == null ? "" : referenceCode;
        this.version = version;
        this.plannedAt = plannedAt;
    }

    /** PLANNED → CANCELLED。確定済(RECEIVED/DISPATCHED)からは取消不可。冪等。 */
    public void cancel() {
        if (status == MovementStatus.CANCELLED) return;
        if (status != MovementStatus.PLANNED) {
            throw new IllegalStateException("確定済の入出庫は取り消せません: " + code.value());
        }
        this.status = MovementStatus.CANCELLED;
    }

    public StockMovementId id() {
        return id;
    }

    public StockMovementCode code() {
        return code;
    }

    public String partnerCode() {
        return partnerCode;
    }

    public String skuCode() {
        return skuCode;
    }

    public String locationId() {
        return locationId;
    }

    public MovementType movementType() {
        return movementType;
    }

    public int quantity() {
        return quantity;
    }

    public MovementStatus status() {
        return status;
    }

    public String referenceCode() {
        return referenceCode;
    }

    public long version() {
        return version;
    }

    public Instant plannedAt() {
        return plannedAt;
    }

    public List<DomainEvent> pendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    public void clearPendingEvents() {
        pendingEvents.clear();
    }
}
