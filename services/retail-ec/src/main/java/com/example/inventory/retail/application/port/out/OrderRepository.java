package com.example.inventory.retail.application.port.out;

import java.util.Optional;

import com.example.inventory.commons.persistence.AggregateRepository;
import com.example.inventory.retail.domain.model.Order;
import com.example.inventory.retail.domain.model.OrderCode;
import com.example.inventory.retail.domain.model.OrderId;

public interface OrderRepository extends AggregateRepository<Order, OrderId> {

    @Override
    Optional<Order> findById(OrderId id);

    @Override
    Order save(Order aggregate);

    @Override
    void delete(Order aggregate);

    boolean existsByCode(OrderCode code);
}
