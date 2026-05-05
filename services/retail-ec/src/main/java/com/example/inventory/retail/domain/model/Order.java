package com.example.inventory.retail.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.retail.domain.event.OrderPlacedEvent;

/**
 * 注文集約ルート。テナント内の注文 1 件を表す。
 *
 * <p>純粋な POJO(ADR-0009)。MyBatis/JPA のアノテーション無し。 楽観ロック用 {@code version} を持ち、save 時は +1 される
 * (commons-persistence 規約)。
 *
 * <p>明細は集約境界内に閉じた Value Object コレクション({@link OrderItem})。集約の外から行単位で 直接更新することは許可しない(Order メソッド経由のみ)。
 */
public final class Order {

    private final OrderId id;
    private final OrderCode code;
    private final String customerEmail;
    private OrderStatus status;
    private final String currency;
    private BigDecimal totalAmount;
    private final List<OrderItem> items;
    private long version;
    private final Instant placedAt;

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    public static Order restore(
            OrderId id,
            OrderCode code,
            String customerEmail,
            OrderStatus status,
            String currency,
            BigDecimal totalAmount,
            List<OrderItem> items,
            long version,
            Instant placedAt) {
        return new Order(
                id, code, customerEmail, status, currency, totalAmount, items, version, placedAt);
    }

    /** 新規注文を確定する({@link OrderPlacedEvent} を発行)。 */
    public static Order place(
            OrderId id,
            OrderCode code,
            String customerEmail,
            String currency,
            List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("注文明細は 1 行以上必要");
        }
        BigDecimal total =
                items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        Order order =
                new Order(
                        id,
                        code,
                        customerEmail,
                        OrderStatus.PLACED,
                        currency,
                        total,
                        items,
                        0L,
                        Instant.now());
        order.pendingEvents.add(
                new OrderPlacedEvent(
                        id.value(),
                        code.value(),
                        customerEmail,
                        currency,
                        total,
                        items.stream()
                                .map(
                                        i ->
                                                new OrderPlacedEvent.Line(
                                                        i.lineNo(),
                                                        i.skuCode(),
                                                        i.locationId(),
                                                        i.quantity(),
                                                        i.unitPrice()))
                                .collect(Collectors.toList()),
                        order.placedAt));
        return order;
    }

    private Order(
            OrderId id,
            OrderCode code,
            String customerEmail,
            OrderStatus status,
            String currency,
            BigDecimal totalAmount,
            List<OrderItem> items,
            long version,
            Instant placedAt) {
        if (customerEmail == null || customerEmail.isBlank()) {
            throw new IllegalArgumentException("customerEmail は必須");
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency は 3 文字 ISO 4217");
        }
        this.id = id;
        this.code = code;
        this.customerEmail = customerEmail;
        this.status = status;
        this.currency = currency;
        this.totalAmount = totalAmount;
        this.items = List.copyOf(items);
        this.version = version;
        this.placedAt = placedAt;
    }

    /** 注文をキャンセルする(MVP はイベント発行なし、状態のみ更新)。 */
    public void cancel() {
        if (status == OrderStatus.CANCELLED) {
            return; // 冪等
        }
        this.status = OrderStatus.CANCELLED;
    }

    public OrderId id() {
        return id;
    }

    public OrderCode code() {
        return code;
    }

    public String customerEmail() {
        return customerEmail;
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
