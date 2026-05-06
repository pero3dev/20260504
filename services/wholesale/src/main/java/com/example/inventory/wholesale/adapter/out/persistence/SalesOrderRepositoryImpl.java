package com.example.inventory.wholesale.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.commons.event.DomainEventPublisher;
import com.example.inventory.commons.persistence.OptimisticLockSupport;
import com.example.inventory.wholesale.application.port.out.SalesOrderRepository;
import com.example.inventory.wholesale.domain.model.Order;
import com.example.inventory.wholesale.domain.model.OrderCode;
import com.example.inventory.wholesale.domain.model.OrderId;
import com.example.inventory.wholesale.domain.model.OrderItem;
import com.example.inventory.wholesale.domain.model.OrderStatus;
import com.example.inventory.wholesale.domain.model.PartnerCode;

@Repository
public class SalesOrderRepositoryImpl implements SalesOrderRepository {

    private final SalesOrderMapper mapper;
    private final DomainEventPublisher eventPublisher;

    public SalesOrderRepositoryImpl(SalesOrderMapper mapper, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        SalesOrderRow row = mapper.findById(id.value());
        if (row == null) return Optional.empty();
        List<OrderItem> items =
                mapper.findItemsByOrderId(row.id()).stream()
                        .map(
                                ir ->
                                        new OrderItem(
                                                ir.lineNo(),
                                                ir.skuCode(),
                                                ir.locationId(),
                                                ir.quantity(),
                                                ir.unitPrice()))
                        .collect(Collectors.toList());
        return Optional.of(
                Order.restore(
                        new OrderId(row.id()),
                        new OrderCode(row.code()),
                        new PartnerCode(row.partnerCode()),
                        OrderStatus.valueOf(row.status()),
                        row.currency(),
                        row.totalAmount(),
                        items,
                        row.requestedDeliveryDate(),
                        row.version(),
                        row.placedAt(),
                        row.shippedAt()));
    }

    @Override
    public boolean existsByCode(OrderCode code) {
        return mapper.existsByCode(code.value()) > 0;
    }

    @Override
    public Order save(Order aggregate) {
        SalesOrderRow row =
                new SalesOrderRow(
                        aggregate.id().value(),
                        aggregate.code().value(),
                        aggregate.partnerCode().value(),
                        aggregate.status().name(),
                        aggregate.totalAmount(),
                        aggregate.currency(),
                        aggregate.requestedDeliveryDate(),
                        aggregate.version() + 1,
                        aggregate.placedAt(),
                        aggregate.shippedAt());

        if (aggregate.version() == 0L) {
            mapper.insert(row);
            for (OrderItem it : aggregate.items()) {
                mapper.insertItem(
                        new SalesOrderItemRow(
                                aggregate.id().value(),
                                it.lineNo(),
                                it.skuCode(),
                                it.locationId(),
                                it.quantity(),
                                it.unitPrice()));
            }
        } else {
            int affected = mapper.update(row, aggregate.version());
            OptimisticLockSupport.verify(
                    affected, "SalesOrder", aggregate.id().value(), aggregate.version());
            // MVP では明細の差分更新を行わない(明細は place() 後不変の前提)。
        }

        for (DomainEvent event : aggregate.pendingEvents()) {
            eventPublisher.publish(event);
        }
        aggregate.clearPendingEvents();
        return aggregate;
    }

    @Override
    public void delete(Order aggregate) {
        mapper.deleteItemsByOrderId(aggregate.id().value());
        int affected = mapper.delete(aggregate.id().value(), aggregate.version());
        OptimisticLockSupport.verify(
                affected, "SalesOrder", aggregate.id().value(), aggregate.version());
    }
}
