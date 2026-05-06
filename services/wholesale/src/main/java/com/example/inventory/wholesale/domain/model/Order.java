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
import com.example.inventory.wholesale.domain.event.SalesOrderShippedEvent;

/**
 * SalesOrder 集約ルート。テナント内の受注 1 件を表す。
 *
 * <p>純粋な POJO(ADR-0009)。MyBatis/JPA のアノテーション無し。 楽観ロック用 {@code version} を持ち、save 時は +1 される。
 *
 * <p>明細は集約境界内に閉じた Value Object コレクション({@link OrderItem})。 取引先別価格(PartnerPrice)は受注時点でスナップショットして
 * unitPrice に焼き付ける。
 *
 * <p>ライフサイクル: PLACED → SHIPPED(終端、cancel 不可)、または PLACED → CANCELLED。 SHIPPED 後の取消は「返品」として別フロー。
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
    private Instant shippedAt;

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
            Instant placedAt,
            Instant shippedAt) {
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
                placedAt,
                shippedAt);
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
                        Instant.now(),
                        null);
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
            Instant placedAt,
            Instant shippedAt) {
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
        this.shippedAt = shippedAt;
    }

    /** 受注をキャンセルする(MVP はイベント発行なし、状態のみ更新)。 PLACED 状態のみ可。SHIPPED 後の取消は返品扱いで別フロー。CANCELLED への再呼出は冪等。 */
    public void cancel() {
        if (status == OrderStatus.CANCELLED) {
            return;
        }
        if (status == OrderStatus.SHIPPED) {
            throw new IllegalStateException("出荷済みの受注はキャンセル不可(返品で別フロー)");
        }
        this.status = OrderStatus.CANCELLED;
    }

    /**
     * 受注を出荷確定する({@link SalesOrderShippedEvent} を発行)。 PLACED → SHIPPED の遷移のみ可。SHIPPED 再呼出は冪等(no-op)。
     * CANCELLED からの呼出は IllegalState。
     */
    public void ship() {
        if (status == OrderStatus.SHIPPED) return; // 冪等
        if (status != OrderStatus.PLACED) {
            throw new IllegalStateException("PLACED 状態の受注のみ出荷確定可。現状態=" + status);
        }
        this.status = OrderStatus.SHIPPED;
        this.shippedAt = Instant.now();
        pendingEvents.add(
                new SalesOrderShippedEvent(
                        id.value(),
                        code.value(),
                        partnerCode.value(),
                        items.stream()
                                .map(
                                        i ->
                                                new SalesOrderShippedEvent.Line(
                                                        i.lineNo(),
                                                        i.skuCode(),
                                                        i.locationId(),
                                                        i.quantity()))
                                .collect(Collectors.toList()),
                        shippedAt,
                        shippedAt));
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

    public Instant shippedAt() {
        return shippedAt;
    }

    public List<DomainEvent> pendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    public void clearPendingEvents() {
        pendingEvents.clear();
    }
}
