package com.example.inventory.wholesale.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.wholesale.domain.event.SalesOrderPlacedEvent;

class OrderTest {

    @Test
    void place_は_PLACED_状態で_合計金額を集計し_SalesOrderPlacedEvent_を発行する() {
        Order order = newOrder();

        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.totalAmount()).isEqualByComparingTo(new BigDecimal("3000"));
        List<DomainEvent> events = order.pendingEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(SalesOrderPlacedEvent.class);
        SalesOrderPlacedEvent ev = (SalesOrderPlacedEvent) events.get(0);
        assertThat(ev.partnerCode()).isEqualTo("PARTNER-ACME");
        assertThat(ev.items()).hasSize(2);
        assertThat(ev.requestedDeliveryDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void 明細が空なら_place_は_IllegalArgumentException() {
        assertThatThrownBy(
                        () ->
                                Order.place(
                                        new OrderId(1L),
                                        new OrderCode("SO-1"),
                                        new PartnerCode("PARTNER-ACME"),
                                        "JPY",
                                        List.of(),
                                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancel_は_CANCELLED_に遷移し_2回呼んでも冪等() {
        Order order = newOrder();
        order.cancel();
        order.cancel();
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void OrderItem_の_lineTotal_は_単価_x_数量() {
        OrderItem item = new OrderItem(1, "SKU-A", "LOC-1", 5, new BigDecimal("400.00"));
        assertThat(item.lineTotal()).isEqualByComparingTo(new BigDecimal("2000.00"));
    }

    @Test
    void OrderItem_unitPrice_が負だと_検証エラー() {
        assertThatThrownBy(() -> new OrderItem(1, "SKU-A", "LOC-1", 1, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void OrderCode_は_3文字以上の_大文字英数とハイフンのみ_を許容() {
        assertThatThrownBy(() -> new OrderCode("so")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderCode("SO_001"))
                .isInstanceOf(IllegalArgumentException.class);
        new OrderCode("SO-001");
    }

    @Test
    void requestedDeliveryDate_は_null_でも_受け入れる() {
        Order order =
                Order.place(
                        new OrderId(1L),
                        new OrderCode("SO-NULL-DATE"),
                        new PartnerCode("PARTNER-ACME"),
                        "JPY",
                        List.of(new OrderItem(1, "SKU-A", "LOC-1", 1, new BigDecimal("100"))),
                        null);
        assertThat(order.requestedDeliveryDate()).isNull();
    }

    private static Order newOrder() {
        return Order.place(
                new OrderId(1L),
                new OrderCode("SO-2026-0001"),
                new PartnerCode("PARTNER-ACME"),
                "JPY",
                List.of(
                        new OrderItem(1, "SKU-A", "LOC-1", 2, new BigDecimal("1000")),
                        new OrderItem(2, "SKU-B", "LOC-1", 1, new BigDecimal("1000"))),
                LocalDate.of(2026, 6, 1));
    }
}
