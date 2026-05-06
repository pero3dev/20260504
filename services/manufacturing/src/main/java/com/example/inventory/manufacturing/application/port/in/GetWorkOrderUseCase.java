package com.example.inventory.manufacturing.application.port.in;

import com.example.inventory.manufacturing.domain.model.WorkOrder;

public interface GetWorkOrderUseCase {

    WorkOrder get(long workOrderId);
}
