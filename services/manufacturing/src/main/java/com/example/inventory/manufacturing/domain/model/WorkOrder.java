package com.example.inventory.manufacturing.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.manufacturing.domain.event.WorkOrderCompletedEvent;
import com.example.inventory.manufacturing.domain.event.WorkOrderReleasedEvent;

/**
 * WorkOrder(製造指図)集約ルート。テナント内の製造 1 件を表す。
 *
 * <p>純粋な POJO(ADR-0009)。MyBatis/JPA のアノテーション無し。 楽観ロック用 {@code version} を持ち、save 時は +1 される。
 *
 * <p>構成要素は集約境界内に閉じた Value Object コレクション({@link BomComponent})。 BOM はマスタだが、{@code place()}
 * 時にスナップショットして指図に焼き付ける。 BOM 改訂後でも既存指図は当時の構成で release/ complete される。
 *
 * <p>ライフサイクル: PLANNED → RELEASED → COMPLETED、または PLANNED/RELEASED → CANCELLED。 release 時に {@link
 * WorkOrderReleasedEvent} を発行(部品引当の起点)、complete 時に {@link WorkOrderCompletedEvent} を発行(完成品入庫の起点)。
 */
public final class WorkOrder {

    private final WorkOrderId id;
    private final WorkOrderCode code;
    private final String productSkuCode;
    private final String locationId;
    private final int plannedQuantity;
    private final List<BomComponent> components;
    private final LocalDate plannedStartDate;
    private WorkOrderStatus status;
    private long version;
    private final Instant placedAt;
    private Instant releasedAt;
    private Instant completedAt;

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    public static WorkOrder restore(
            WorkOrderId id,
            WorkOrderCode code,
            String productSkuCode,
            String locationId,
            int plannedQuantity,
            List<BomComponent> components,
            LocalDate plannedStartDate,
            WorkOrderStatus status,
            long version,
            Instant placedAt,
            Instant releasedAt,
            Instant completedAt) {
        return new WorkOrder(
                id,
                code,
                productSkuCode,
                locationId,
                plannedQuantity,
                components,
                plannedStartDate,
                status,
                version,
                placedAt,
                releasedAt,
                completedAt);
    }

    /** 新規製造指図を計画する。状態は PLANNED。イベントは発行しない(release 時に発行)。 */
    public static WorkOrder place(
            WorkOrderId id,
            WorkOrderCode code,
            String productSkuCode,
            String locationId,
            int plannedQuantity,
            List<BomComponent> components,
            LocalDate plannedStartDate) {
        return new WorkOrder(
                id,
                code,
                productSkuCode,
                locationId,
                plannedQuantity,
                components,
                plannedStartDate,
                WorkOrderStatus.PLANNED,
                0L,
                Instant.now(),
                null,
                null);
    }

    private WorkOrder(
            WorkOrderId id,
            WorkOrderCode code,
            String productSkuCode,
            String locationId,
            int plannedQuantity,
            List<BomComponent> components,
            LocalDate plannedStartDate,
            WorkOrderStatus status,
            long version,
            Instant placedAt,
            Instant releasedAt,
            Instant completedAt) {
        if (productSkuCode == null || productSkuCode.isBlank())
            throw new IllegalArgumentException("productSkuCode は必須");
        if (locationId == null || locationId.isBlank())
            throw new IllegalArgumentException("locationId は必須");
        if (plannedQuantity <= 0) throw new IllegalArgumentException("plannedQuantity は正の値");
        if (components == null || components.isEmpty())
            throw new IllegalArgumentException("構成要素は 1 つ以上必要");
        this.id = id;
        this.code = code;
        this.productSkuCode = productSkuCode;
        this.locationId = locationId;
        this.plannedQuantity = plannedQuantity;
        this.components = List.copyOf(components);
        this.plannedStartDate = plannedStartDate;
        this.status = status;
        this.version = version;
        this.placedAt = placedAt;
        this.releasedAt = releasedAt;
        this.completedAt = completedAt;
    }

    /** 指図を着手指示状態(RELEASED)に遷移し、{@link WorkOrderReleasedEvent} を発行。 */
    public void release() {
        if (status == WorkOrderStatus.RELEASED) return; // 冪等
        if (status != WorkOrderStatus.PLANNED) {
            throw new IllegalStateException("PLANNED 状態の指図のみ release 可能。現状態=" + status);
        }
        this.status = WorkOrderStatus.RELEASED;
        this.releasedAt = Instant.now();
        pendingEvents.add(
                new WorkOrderReleasedEvent(
                        id.value(),
                        code.value(),
                        productSkuCode,
                        locationId,
                        plannedQuantity,
                        components.stream()
                                .map(
                                        c ->
                                                new WorkOrderReleasedEvent.Component(
                                                        c.componentSkuCode(),
                                                        c.quantityPerUnit() * plannedQuantity))
                                .collect(Collectors.toList()),
                        plannedStartDate,
                        releasedAt));
    }

    /**
     * 指図を完了状態(COMPLETED)に遷移し、{@link WorkOrderCompletedEvent} を発行。 RELEASED 状態のみ可。COMPLETED
     * への再呼出は冪等(no-op)。
     *
     * <p>イベントは Inventory Core 側で受信され、{@code productSkuCode} の {@code
     * Inventory.receive(plannedQuantity)} を呼び出して 完成品在庫が増える(L3 で配線)。
     */
    public void complete() {
        if (status == WorkOrderStatus.COMPLETED) return; // 冪等
        if (status != WorkOrderStatus.RELEASED) {
            throw new IllegalStateException("RELEASED 状態の指図のみ complete 可能。現状態=" + status);
        }
        this.status = WorkOrderStatus.COMPLETED;
        this.completedAt = Instant.now();
        pendingEvents.add(
                new WorkOrderCompletedEvent(
                        id.value(),
                        code.value(),
                        productSkuCode,
                        locationId,
                        plannedQuantity,
                        completedAt,
                        completedAt));
    }

    /** 指図をキャンセル。冪等。COMPLETED は不可。 */
    public void cancel() {
        if (status == WorkOrderStatus.CANCELLED) return;
        if (status == WorkOrderStatus.COMPLETED) {
            throw new IllegalStateException("COMPLETED 状態の指図はキャンセル不可");
        }
        this.status = WorkOrderStatus.CANCELLED;
    }

    public WorkOrderId id() {
        return id;
    }

    public WorkOrderCode code() {
        return code;
    }

    public String productSkuCode() {
        return productSkuCode;
    }

    public String locationId() {
        return locationId;
    }

    public int plannedQuantity() {
        return plannedQuantity;
    }

    public List<BomComponent> components() {
        return Collections.unmodifiableList(components);
    }

    public LocalDate plannedStartDate() {
        return plannedStartDate;
    }

    public WorkOrderStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public Instant placedAt() {
        return placedAt;
    }

    public Instant releasedAt() {
        return releasedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public List<DomainEvent> pendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    public void clearPendingEvents() {
        pendingEvents.clear();
    }
}
