package com.example.inventory.wholesale.application.port.in;

import com.example.inventory.wholesale.domain.model.Order;

public interface GetSalesOrderUseCase {

    Order get(long orderId);
}
