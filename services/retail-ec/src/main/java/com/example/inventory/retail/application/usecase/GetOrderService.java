package com.example.inventory.retail.application.usecase;

import org.springframework.stereotype.Service;

import com.example.inventory.retail.application.port.in.GetOrderUseCase;
import com.example.inventory.retail.application.port.in.OrderNotFoundException;
import com.example.inventory.retail.application.port.out.OrderRepository;
import com.example.inventory.retail.domain.model.Order;
import com.example.inventory.retail.domain.model.OrderId;

@Service
public class GetOrderService implements GetOrderUseCase {

    private final OrderRepository repository;

    public GetOrderService(OrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public Order get(long orderId) {
        return repository
                .findById(new OrderId(orderId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
