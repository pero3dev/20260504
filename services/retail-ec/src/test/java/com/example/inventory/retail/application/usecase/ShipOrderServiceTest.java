package com.example.inventory.retail.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.inventory.retail.application.port.in.OrderNotFoundException;
import com.example.inventory.retail.application.port.in.OrderStateConflictException;
import com.example.inventory.retail.application.port.out.OrderRepository;
import com.example.inventory.retail.domain.model.Order;
import com.example.inventory.retail.domain.model.OrderCode;
import com.example.inventory.retail.domain.model.OrderId;
import com.example.inventory.retail.domain.model.OrderItem;
import com.example.inventory.retail.domain.model.OrderStatus;

class ShipOrderServiceTest {

    private OrderRepository repository;
    private ShipOrderService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(OrderRepository.class);
        when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new ShipOrderService(repository);
    }

    @Test
    void 既存_PLACED_注文は_SHIPPED_に遷移して_save_される() {
        Order placed = newPlaced();
        when(repository.findById(new OrderId(1L))).thenReturn(Optional.of(placed));

        Order result = service.ship(1L);

        assertThat(result.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(result.shippedAt()).isNotNull();
        verify(repository).save(placed);
    }

    @Test
    void 注文が見つからなければ_OrderNotFoundException() {
        when(repository.findById(new OrderId(1L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ship(1L)).isInstanceOf(OrderNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void CANCELLED_注文に対する_ship_は_OrderStateConflictException_に変換される() {
        Order o = newPlaced();
        o.cancel();
        when(repository.findById(new OrderId(1L))).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.ship(1L)).isInstanceOf(OrderStateConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void SHIPPED_注文に対する_ship_は_冪等で_save_は呼ばれる_が_状態は_SHIPPED() {
        Order o = newPlaced();
        o.ship();
        when(repository.findById(new OrderId(1L))).thenReturn(Optional.of(o));

        Order result = service.ship(1L);

        assertThat(result.status()).isEqualTo(OrderStatus.SHIPPED);
        verify(repository).save(o);
    }

    private static Order newPlaced() {
        return Order.place(
                new OrderId(1L),
                new OrderCode("ORD-2026-0001"),
                "alice@example.com",
                "JPY",
                List.of(new OrderItem(1, "SKU-A", "LOC-1", 2, new BigDecimal("1000"))));
    }
}
