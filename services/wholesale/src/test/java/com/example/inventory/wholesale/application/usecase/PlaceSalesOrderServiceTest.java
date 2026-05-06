package com.example.inventory.wholesale.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.wholesale.application.port.in.DuplicateOrderCodeException;
import com.example.inventory.wholesale.application.port.in.PartnerPriceNotFoundException;
import com.example.inventory.wholesale.application.port.in.PlaceSalesOrderUseCase;
import com.example.inventory.wholesale.application.port.out.PartnerPriceRepository;
import com.example.inventory.wholesale.application.port.out.SalesOrderRepository;
import com.example.inventory.wholesale.domain.model.Order;
import com.example.inventory.wholesale.domain.model.OrderCode;
import com.example.inventory.wholesale.domain.model.PartnerCode;
import com.example.inventory.wholesale.domain.model.PartnerPrice;

class PlaceSalesOrderServiceTest {

    private SalesOrderRepository orderRepository;
    private PartnerPriceRepository priceRepository;
    private SnowflakeIdGenerator idGenerator;
    private PlaceSalesOrderService service;

    @BeforeEach
    void setUp() {
        orderRepository = Mockito.mock(SalesOrderRepository.class);
        priceRepository = Mockito.mock(PartnerPriceRepository.class);
        idGenerator = Mockito.mock(SnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(100L);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new PlaceSalesOrderService(orderRepository, priceRepository, idGenerator);
    }

    @Test
    void unitPrice_は_PartnerPrice_から引いて埋められる_リクエストでは渡さない() {
        when(orderRepository.existsByCode(any(OrderCode.class))).thenReturn(false);
        when(priceRepository.findCurrent(any(PartnerCode.class), eq("SKU-A")))
                .thenReturn(
                        Optional.of(
                                new PartnerPrice(
                                        new PartnerCode("PARTNER-ACME"),
                                        "SKU-A",
                                        new BigDecimal("800"),
                                        "JPY")));
        when(priceRepository.findCurrent(any(PartnerCode.class), eq("SKU-B")))
                .thenReturn(
                        Optional.of(
                                new PartnerPrice(
                                        new PartnerCode("PARTNER-ACME"),
                                        "SKU-B",
                                        new BigDecimal("1500"),
                                        "JPY")));

        Order created =
                service.place(
                        new PlaceSalesOrderUseCase.Command(
                                "SO-2026-0001",
                                "PARTNER-ACME",
                                "JPY",
                                List.of(
                                        new PlaceSalesOrderUseCase.Command.Line(
                                                "SKU-A", "LOC-1", 2),
                                        new PlaceSalesOrderUseCase.Command.Line(
                                                "SKU-B", "LOC-1", 1)),
                                null));

        assertThat(created.items()).hasSize(2);
        assertThat(created.items().get(0).unitPrice()).isEqualByComparingTo(new BigDecimal("800"));
        assertThat(created.items().get(1).unitPrice()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(created.totalAmount()).isEqualByComparingTo(new BigDecimal("3100"));
    }

    @Test
    void 重複_OrderCode_なら_DuplicateOrderCodeException() {
        when(orderRepository.existsByCode(any(OrderCode.class))).thenReturn(true);

        assertThatThrownBy(() -> service.place(simpleCmd()))
                .isInstanceOf(DuplicateOrderCodeException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void PartnerPrice_未登録なら_PartnerPriceNotFoundException() {
        when(orderRepository.existsByCode(any(OrderCode.class))).thenReturn(false);
        when(priceRepository.findCurrent(any(PartnerCode.class), eq("SKU-A")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.place(simpleCmd()))
                .isInstanceOf(PartnerPriceNotFoundException.class);
        verify(orderRepository, never()).save(any());
    }

    private static PlaceSalesOrderUseCase.Command simpleCmd() {
        return new PlaceSalesOrderUseCase.Command(
                "SO-2026-0001",
                "PARTNER-ACME",
                "JPY",
                List.of(new PlaceSalesOrderUseCase.Command.Line("SKU-A", "LOC-1", 2)),
                null);
    }
}
