package com.example.inventory.wholesale.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.example.inventory.wholesale.application.port.in.HandleReservationFailureUseCase;
import com.example.inventory.wholesale.application.port.out.SalesOrderRepository;
import com.example.inventory.wholesale.domain.model.Order;
import com.example.inventory.wholesale.domain.model.OrderCode;
import com.example.inventory.wholesale.domain.model.OrderId;
import com.example.inventory.wholesale.domain.model.OrderItem;
import com.example.inventory.wholesale.domain.model.OrderStatus;
import com.example.inventory.wholesale.domain.model.PartnerCode;

class HandleReservationFailureServiceTest {

    private SalesOrderRepository repository;
    private HandleReservationFailureService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(SalesOrderRepository.class);
        when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new HandleReservationFailureService(repository);
    }

    @Test
    void 既存_PLACED_受注は_CANCELLED_に遷移して_save_される() {
        Order placed = newPlacedOrder();
        when(repository.findById(new OrderId(1L))).thenReturn(Optional.of(placed));

        service.handle(
                new HandleReservationFailureUseCase.Command(
                        1L, "SO-001", "ERR_INVENTORY_INSUFFICIENT", "在庫不足"));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void 受注が見つからなければ_save_されず_スキップ() {
        when(repository.findById(new OrderId(1L))).thenReturn(Optional.empty());

        service.handle(
                new HandleReservationFailureUseCase.Command(
                        1L, "SO-001", "ERR_INVENTORY_INSUFFICIENT", "在庫不足"));

        verify(repository, never()).save(any());
    }

    @Test
    void すでに_CANCELLED_の受注に対する補償は_冪等で_状態は_CANCELLED_のまま() {
        Order placed = newPlacedOrder();
        placed.cancel();
        when(repository.findById(new OrderId(1L))).thenReturn(Optional.of(placed));

        service.handle(
                new HandleReservationFailureUseCase.Command(
                        1L, "SO-001", "ERR_INVENTORY_INSUFFICIENT", "在庫不足"));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void すでに_SHIPPED_の受注に対する補償は_整合性監査に任せて_スキップ_save_されない() {
        Order placed = newPlacedOrder();
        placed.ship();
        when(repository.findById(new OrderId(1L))).thenReturn(Optional.of(placed));

        service.handle(
                new HandleReservationFailureUseCase.Command(
                        1L, "SO-001", "ERR_INVENTORY_INSUFFICIENT", "在庫不足"));

        verify(repository, never()).save(any());
    }

    private static Order newPlacedOrder() {
        return Order.place(
                new OrderId(1L),
                new OrderCode("SO-001"),
                new PartnerCode("PARTNER-ACME"),
                "JPY",
                List.of(new OrderItem(1, "SKU-A", "LOC-1", 1, new BigDecimal("100"))),
                null);
    }
}
