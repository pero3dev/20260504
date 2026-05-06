package com.example.inventory.wholesale.application.usecase;

import org.springframework.stereotype.Service;

import com.example.inventory.wholesale.application.port.in.GetSalesOrderUseCase;
import com.example.inventory.wholesale.application.port.in.OrderNotFoundException;
import com.example.inventory.wholesale.application.port.out.SalesOrderRepository;
import com.example.inventory.wholesale.domain.model.Order;
import com.example.inventory.wholesale.domain.model.OrderId;

@Service
public class GetSalesOrderService implements GetSalesOrderUseCase {

    private final SalesOrderRepository repository;

    public GetSalesOrderService(SalesOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public Order get(long orderId) {
        return repository
                .findById(new OrderId(orderId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
