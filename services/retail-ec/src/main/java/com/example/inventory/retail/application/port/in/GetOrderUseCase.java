package com.example.inventory.retail.application.port.in;

import com.example.inventory.retail.domain.model.Order;

public interface GetOrderUseCase {

    Order get(long orderId);
}
