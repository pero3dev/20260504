package com.example.inventory.retail.adapter.in.rest;

import java.math.BigDecimal;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.retail.adapter.in.rest.api.OrdersApi;
import com.example.inventory.retail.adapter.in.rest.api.model.OrderLine;
import com.example.inventory.retail.adapter.in.rest.api.model.OrderResponse;
import com.example.inventory.retail.adapter.in.rest.api.model.PlaceOrderRequest;
import com.example.inventory.retail.application.port.in.GetOrderUseCase;
import com.example.inventory.retail.application.port.in.PlaceOrderUseCase;
import com.example.inventory.retail.application.port.in.ShipOrderUseCase;
import com.example.inventory.retail.domain.model.Order;

/** Order REST 入力アダプタ。OpenAPI 生成 {@link OrdersApi} を実装(ADR-0006)。 */
@RestController
public class OrderController implements OrdersApi {

    private final PlaceOrderUseCase placeOrder;
    private final ShipOrderUseCase shipOrder;
    private final GetOrderUseCase getOrder;

    public OrderController(
            PlaceOrderUseCase placeOrder, ShipOrderUseCase shipOrder, GetOrderUseCase getOrder) {
        this.placeOrder = placeOrder;
        this.shipOrder = shipOrder;
        this.getOrder = getOrder;
    }

    @Override
    public ResponseEntity<OrderResponse> placeOrder(PlaceOrderRequest request) {
        var lines =
                request.getItems().stream()
                        .map(
                                l ->
                                        new PlaceOrderUseCase.Command.Line(
                                                l.getSkuCode(),
                                                l.getLocationId(),
                                                l.getQuantity(),
                                                BigDecimal.valueOf(l.getUnitPrice())))
                        .collect(Collectors.toList());
        Order created =
                placeOrder.place(
                        new PlaceOrderUseCase.Command(
                                request.getCode(),
                                request.getCustomerEmail(),
                                request.getCurrency(),
                                lines));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @Override
    public ResponseEntity<OrderResponse> getOrder(Long orderId) {
        return ResponseEntity.ok(toResponse(getOrder.get(orderId)));
    }

    @Override
    public ResponseEntity<OrderResponse> shipOrder(Long orderId) {
        return ResponseEntity.ok(toResponse(shipOrder.ship(orderId)));
    }

    private static OrderResponse toResponse(Order order) {
        OrderResponse r = new OrderResponse();
        r.setId(order.id().value());
        r.setCode(order.code().value());
        r.setCustomerEmail(order.customerEmail());
        r.setStatus(OrderResponse.StatusEnum.valueOf(order.status().name()));
        r.setCurrency(order.currency());
        r.setTotalAmount(order.totalAmount().doubleValue());
        r.setItems(
                order.items().stream()
                        .map(
                                it -> {
                                    OrderLine line = new OrderLine();
                                    line.setSkuCode(it.skuCode());
                                    line.setLocationId(it.locationId());
                                    line.setQuantity(it.quantity());
                                    line.setUnitPrice(it.unitPrice().doubleValue());
                                    return line;
                                })
                        .collect(Collectors.toList()));
        r.setVersion(order.version() + 1);
        return r;
    }
}
