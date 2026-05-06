package com.example.inventory.manufacturing.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.commons.event.DomainEventPublisher;
import com.example.inventory.commons.persistence.OptimisticLockSupport;
import com.example.inventory.manufacturing.application.port.out.WorkOrderRepository;
import com.example.inventory.manufacturing.domain.model.BomComponent;
import com.example.inventory.manufacturing.domain.model.WorkOrder;
import com.example.inventory.manufacturing.domain.model.WorkOrderCode;
import com.example.inventory.manufacturing.domain.model.WorkOrderId;
import com.example.inventory.manufacturing.domain.model.WorkOrderStatus;

@Repository
public class WorkOrderRepositoryImpl implements WorkOrderRepository {

    private final WorkOrderMapper mapper;
    private final DomainEventPublisher eventPublisher;

    public WorkOrderRepositoryImpl(WorkOrderMapper mapper, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<WorkOrder> findById(WorkOrderId id) {
        WorkOrderRow row = mapper.findById(id.value());
        if (row == null) return Optional.empty();
        List<BomComponent> components =
                mapper.findComponentsByWorkOrderId(row.id()).stream()
                        .map(c -> new BomComponent(c.componentSkuCode(), c.quantityPerUnit()))
                        .collect(Collectors.toList());
        return Optional.of(
                WorkOrder.restore(
                        new WorkOrderId(row.id()),
                        new WorkOrderCode(row.code()),
                        row.productSkuCode(),
                        row.locationId(),
                        row.plannedQuantity(),
                        components,
                        row.plannedStartDate(),
                        WorkOrderStatus.valueOf(row.status()),
                        row.version(),
                        row.placedAt(),
                        row.releasedAt(),
                        row.completedAt()));
    }

    @Override
    public boolean existsByCode(WorkOrderCode code) {
        return mapper.existsByCode(code.value()) > 0;
    }

    @Override
    public WorkOrder save(WorkOrder aggregate) {
        WorkOrderRow row =
                new WorkOrderRow(
                        aggregate.id().value(),
                        aggregate.code().value(),
                        aggregate.productSkuCode(),
                        aggregate.locationId(),
                        aggregate.plannedQuantity(),
                        aggregate.status().name(),
                        aggregate.plannedStartDate(),
                        aggregate.version() + 1,
                        aggregate.placedAt(),
                        aggregate.releasedAt(),
                        aggregate.completedAt());

        if (aggregate.version() == 0L) {
            mapper.insert(row);
            int lineNo = 1;
            for (BomComponent c : aggregate.components()) {
                mapper.insertComponent(
                        new WorkOrderComponentRow(
                                aggregate.id().value(),
                                lineNo++,
                                c.componentSkuCode(),
                                c.quantityPerUnit()));
            }
        } else {
            int affected = mapper.update(row, aggregate.version());
            OptimisticLockSupport.verify(
                    affected, "WorkOrder", aggregate.id().value(), aggregate.version());
            // MVP では構成要素の差分更新を行わない(place 後不変の前提)。
        }

        for (DomainEvent event : aggregate.pendingEvents()) {
            eventPublisher.publish(event);
        }
        aggregate.clearPendingEvents();
        return aggregate;
    }

    @Override
    public void delete(WorkOrder aggregate) {
        mapper.deleteComponentsByWorkOrderId(aggregate.id().value());
        int affected = mapper.delete(aggregate.id().value(), aggregate.version());
        OptimisticLockSupport.verify(
                affected, "WorkOrder", aggregate.id().value(), aggregate.version());
    }
}
