package com.example.inventory.manufacturing.application.port.out;

import java.util.Optional;

import com.example.inventory.commons.persistence.AggregateRepository;
import com.example.inventory.manufacturing.domain.model.WorkOrder;
import com.example.inventory.manufacturing.domain.model.WorkOrderCode;
import com.example.inventory.manufacturing.domain.model.WorkOrderId;

public interface WorkOrderRepository extends AggregateRepository<WorkOrder, WorkOrderId> {

    @Override
    Optional<WorkOrder> findById(WorkOrderId id);

    @Override
    WorkOrder save(WorkOrder aggregate);

    @Override
    void delete(WorkOrder aggregate);

    boolean existsByCode(WorkOrderCode code);
}
