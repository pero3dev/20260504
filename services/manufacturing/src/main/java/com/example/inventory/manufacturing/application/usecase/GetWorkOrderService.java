package com.example.inventory.manufacturing.application.usecase;

import org.springframework.stereotype.Service;

import com.example.inventory.manufacturing.application.port.in.GetWorkOrderUseCase;
import com.example.inventory.manufacturing.application.port.in.WorkOrderNotFoundException;
import com.example.inventory.manufacturing.application.port.out.WorkOrderRepository;
import com.example.inventory.manufacturing.domain.model.WorkOrder;
import com.example.inventory.manufacturing.domain.model.WorkOrderId;

@Service
public class GetWorkOrderService implements GetWorkOrderUseCase {

    private final WorkOrderRepository repository;

    public GetWorkOrderService(WorkOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public WorkOrder get(long workOrderId) {
        return repository
                .findById(new WorkOrderId(workOrderId))
                .orElseThrow(() -> new WorkOrderNotFoundException(workOrderId));
    }
}
