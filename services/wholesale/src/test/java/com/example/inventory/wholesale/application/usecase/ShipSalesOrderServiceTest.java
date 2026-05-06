package com.example.inventory.wholesale.application.usecase;

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

import com.example.inventory.wholesale.application.port.in.OrderNotFoundException;
import com.example.inventory.wholesale.application.port.in.OrderStateConflictException;
import com.example.inventory.wholesale.application.port.out.SalesOrderRepository;
import com.example.inventory.wholesale.domain.model.Order;
import com.example.inventory.wholesale.domain.model.OrderCode;
import com.example.inventory.wholesale.domain.model.OrderId;
import com.example.inventory.wholesale.domain.model.OrderItem;
import com.example.inventory.wholesale.domain.model.OrderStatus;
import com.example.inventory.wholesale.domain.model.PartnerCode;

class ShipSalesOrderServiceTest {

    private SalesOrderRepository repository;
    private ShipSalesOrderService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(SalesOrderRepository.class);
        when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new ShipSalesOrderService(repository);
    }

    @Test
    void 既存_PLACED_受注は_SHIPPED_に遷移して_save_される() {
        Order placed = newPlaced();
        when(repository.findById(new OrderId(1L))).thenReturn(Optional.of(placed));

        Order result = service.ship(1L);

        assertThat(result.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(result.shippedAt()).isNotNull();
        verify(repository).save(placed);
    }

    @Test
    void 受注が見つからなければ_OrderNotFoundException() {
        when(repository.findById(new OrderId(1L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ship(1L)).isInstanceOf(OrderNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void CANCELLED_受注に対する_ship_は_OrderStateConflictException_に変換される() {
        Order o = newPlaced();
        o.cancel();
        when(repository.findById(new OrderId(1L))).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.ship(1L)).isInstanceOf(OrderStateConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void SHIPPED_受注に対する_ship_は_冪等で_save_は呼ばれる_が_状態は_SHIPPED() {
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
                new OrderCode("SO-2026-0001"),
                new PartnerCode("PARTNER-ACME"),
                "JPY",
                List.of(new OrderItem(1, "SKU-A", "LOC-1", 2, new BigDecimal("1000"))),
                null);
    }
}
