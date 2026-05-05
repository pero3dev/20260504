package com.example.inventory.retail.application.usecase;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.retail.application.port.in.DuplicateOrderCodeException;
import com.example.inventory.retail.application.port.in.PlaceOrderUseCase;
import com.example.inventory.retail.application.port.out.OrderRepository;
import com.example.inventory.retail.domain.model.Order;
import com.example.inventory.retail.domain.model.OrderCode;
import com.example.inventory.retail.domain.model.OrderId;
import com.example.inventory.retail.domain.model.OrderItem;

@Service
public class PlaceOrderService implements PlaceOrderUseCase {

    private final OrderRepository repository;
    private final SnowflakeIdGenerator idGenerator;

    public PlaceOrderService(OrderRepository repository, SnowflakeIdGenerator idGenerator) {
        this.repository = repository;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    @Auditable(action = "ORDER_PLACE", targetType = "Order", targetIdExpression = "#command.code")
    public Order place(Command command) {
        OrderCode code = new OrderCode(command.code());
        if (repository.existsByCode(code)) {
            throw new DuplicateOrderCodeException(command.code());
        }
        OrderId id = new OrderId(idGenerator.nextId());

        List<OrderItem> items = new ArrayList<>();
        int lineNo = 1;
        for (Command.Line l : command.items()) {
            items.add(
                    new OrderItem(
                            lineNo++, l.skuCode(), l.locationId(), l.quantity(), l.unitPrice()));
        }
        Order order = Order.place(id, code, command.customerEmail(), command.currency(), items);
        return repository.save(order);
    }
}
