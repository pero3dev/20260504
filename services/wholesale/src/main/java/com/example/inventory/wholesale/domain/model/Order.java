package com.example.inventory.wholesale.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.wholesale.domain.event.SalesOrderPlacedEvent;

/**
 * SalesOrder 集約ルート。テナント内の受注 1 件を表す。
 *
 * <p>純粋な POJO(ADR-0009)。MyBatis/JPA のアノテーション無し。 楽観ロック用 {@code version} を持ち、save 時は +1 される。
 *
 * <p>明細は集約境界内に閉じた Value Object コレクション({@link OrderItem})。 取引先別価格(PartnerPrice)は受注時点でスナップショットして
 * unitPrice に焼き付ける。
 */
public final class Order {

    private final OrderId id;
    private final OrderCode code;
    private final PartnerCode partnerCode;
    private OrderStatus status;
    private final String currency;
    private BigDecimal totalAmount;
    private final List<OrderItem> items;
    private final LocalDate requestedDeliveryDate;
    private long version;
    private final Instant placedAt;

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    public static Order restore(
            OrderId id,
            OrderCode code,
            PartnerCode partnerCode,
            OrderStatus status,
            String currency,
            BigDecimal totalAmount,
            List<OrderItem> items,
            LocalDate requestedDeliveryDate,
            long version,
            Instant placedAt) {
        return new Order(
                id,
                code,
                partnerCode,
                status,
                currency,
                totalAmount,
                items,
                requestedDeliveryDate,
                version,
                placedAt);
    }

    /** 新規受注を確定する({@link SalesOrderPlacedEvent} を発行)。 */
    public static Order place(
            OrderId id,
            OrderCode code,
            PartnerCode partnerCode,
            String currency,
            List<OrderItem> items,
            LocalDate requestedDeliveryDate) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("受注明細は 1 行以上必要");
        }
        BigDecimal total =
                items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        Order order =
                new Order(
                        id,
                        code,
                        partnerCode,
                        OrderStatus.PLACED,
                        currency,
                        total,
                        items,
                        requestedDeliveryDate,
                        0L,
                        Instant.now());
        order.pendingEvents.add(
                new SalesOrderPlacedEvent(
                        id.value(),
                        code.value(),
                        partnerCode.value(),
                        currency,
                        total,
                        items.stream()
                                .map(
                                        i ->
                                                new SalesOrderPlacedEvent.Line(
                                                        i.lineNo(),
                                                        i.skuCode(),
                                                        i.locationId(),
                                                        i.quantity(),
                                                        i.unitPrice()))
                                .collect(Collectors.toList()),
                        requestedDeliveryDate,
                        order.placedAt));
        return order;
    }

    private Order(
            OrderId id,
            OrderCode code,
            PartnerCode partnerCode,
            OrderStatus status,
            String currency,
            BigDecimal totalAmount,
            List<OrderItem> items,
            LocalDate requestedDeliveryDate,
            long version,
            Instant placedAt) {
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency は 3 文字 ISO 4217");
        }
        this.id = id;
        this.code = code;
        this.partnerCode = partnerCode;
        this.status = status;
        this.currency = currency;
        this.totalAmount = totalAmount;
        this.items = List.copyOf(items);
        this.requestedDeliveryDate = requestedDeliveryDate;
        this.version = version;
        this.placedAt = placedAt;
    }

    /** 受注をキャンセルする(MVP はイベント発行なし、状態のみ更新)。冪等。 */
    public void cancel() {
        if (status == OrderStatus.CANCELLED) {
            return;
        }
        this.status = OrderStatus.CANCELLED;
    }

    public OrderId id() {
        return id;
    }

    public OrderCode code() {
        return code;
    }

    public PartnerCode partnerCode() {
        return partnerCode;
    }

    public OrderStatus status() {
        return status;
    }

    public String currency() {
        return currency;
    }

    public BigDecimal totalAmount() {
        return totalAmount;
    }

    public List<OrderItem> items() {
        return Collections.unmodifiableList(items);
    }

    public LocalDate requestedDeliveryDate() {
        return requestedDeliveryDate;
    }

    public long version() {
        return version;
    }

    public Instant placedAt() {
        return placedAt;
    }

    public List<DomainEvent> pendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    public void clearPendingEvents() {
        pendingEvents.clear();
    }
}
