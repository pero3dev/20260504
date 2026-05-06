package com.example.inventory.retail.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.inventory.retail.domain.event.OrderPlacedEvent;
import com.example.inventory.retail.domain.event.OrderShippedEvent;

class OrderTest {

    private static final OrderId ID = new OrderId(400_000_000_001L);
    private static final OrderCode CODE = new OrderCode("ORD-2026-0001");

    private static OrderItem line(int no, String sku, int qty, String price) {
        return new OrderItem(no, sku, "LOC-1", qty, new BigDecimal(price));
    }

    @Test
    void place_は_OrderPlacedEvent_を発行し合計金額を計算する() {
        Order order =
                Order.place(
                        ID,
                        CODE,
                        "alice@example.com",
                        "JPY",
                        List.of(line(1, "SKU-A", 2, "150"), line(2, "SKU-B", 3, "200")));

        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.totalAmount()).isEqualByComparingTo("900"); // 2*150 + 3*200
        assertThat(order.items()).hasSize(2);
        assertThat(order.pendingEvents()).hasSize(1);
        assertThat(order.pendingEvents().get(0)).isInstanceOf(OrderPlacedEvent.class);

        OrderPlacedEvent ev = (OrderPlacedEvent) order.pendingEvents().get(0);
        assertThat(ev.aggregateId()).isEqualTo(ID.value());
        assertThat(ev.code()).isEqualTo(CODE.value());
        assertThat(ev.items()).hasSize(2);
        assertThat(ev.totalAmount()).isEqualByComparingTo("900");
    }

    @Test
    void 明細が空の注文は拒否される() {
        assertThatThrownBy(() -> Order.place(ID, CODE, "alice@example.com", "JPY", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("明細");
    }

    @Test
    void cancel_は冪等() {
        Order order =
                Order.place(ID, CODE, "alice@example.com", "JPY", List.of(line(1, "S", 1, "100")));
        order.cancel();
        order.cancel(); // 2 度目は no-op

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void ship_は_SHIPPED_に遷移し_OrderShippedEvent_を発行する() {
        Order order =
                Order.place(
                        ID,
                        CODE,
                        "alice@example.com",
                        "JPY",
                        List.of(line(1, "SKU-A", 2, "150"), line(2, "SKU-B", 3, "200")));
        order.clearPendingEvents(); // place イベントを除外して ship イベントだけ確認

        order.ship();

        assertThat(order.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.shippedAt()).isNotNull();
        assertThat(order.pendingEvents()).hasSize(1);
        OrderShippedEvent ev = (OrderShippedEvent) order.pendingEvents().get(0);
        assertThat(ev.code()).isEqualTo(CODE.value());
        assertThat(ev.customerEmail()).isEqualTo("alice@example.com");
        assertThat(ev.items()).hasSize(2);
        assertThat(ev.items().get(0).skuCode()).isEqualTo("SKU-A");
        assertThat(ev.items().get(0).quantity()).isEqualTo(2);
    }

    @Test
    void ship_を_2回呼んでも_2回目は_冪等で_イベントは_1回だけ() {
        Order order =
                Order.place(ID, CODE, "alice@example.com", "JPY", List.of(line(1, "S", 1, "100")));
        order.ship();
        order.clearPendingEvents();

        order.ship(); // 既に SHIPPED → no-op

        assertThat(order.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.pendingEvents()).isEmpty();
    }

    @Test
    void CANCELLED_状態の注文に_ship_は_IllegalState() {
        Order order =
                Order.place(ID, CODE, "alice@example.com", "JPY", List.of(line(1, "S", 1, "100")));
        order.cancel();

        assertThatThrownBy(order::ship).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void SHIPPED_状態の注文に_cancel_は_IllegalState_出荷済みは返品で別フロー() {
        Order order =
                Order.place(ID, CODE, "alice@example.com", "JPY", List.of(line(1, "S", 1, "100")));
        order.ship();

        assertThatThrownBy(order::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void OrderCode_は大文字英数とハイフンのみ() {
        assertThatThrownBy(() -> new OrderCode("ord-lower"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderCode("OR")).isInstanceOf(IllegalArgumentException.class);
        new OrderCode("ORD-2026-0001");
    }

    @Test
    void OrderItem_の_lineTotal_計算() {
        OrderItem it = line(1, "SKU-X", 4, "250");
        assertThat(it.lineTotal()).isEqualByComparingTo("1000");
    }
}
