package com.example.inventory.wholesale.application.port.out;

import java.util.Optional;

import com.example.inventory.commons.persistence.AggregateRepository;
import com.example.inventory.wholesale.domain.model.Order;
import com.example.inventory.wholesale.domain.model.OrderCode;
import com.example.inventory.wholesale.domain.model.OrderId;

public interface SalesOrderRepository extends AggregateRepository<Order, OrderId> {

    @Override
    Optional<Order> findById(OrderId id);

    @Override
    Order save(Order aggregate);

    @Override
    void delete(Order aggregate);

    boolean existsByCode(OrderCode code);
}
