package com.example.inventory.wholesale.adapter.in.rest;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.wholesale.adapter.in.rest.api.SalesOrdersApi;
import com.example.inventory.wholesale.adapter.in.rest.api.model.PlaceSalesOrderRequest;
import com.example.inventory.wholesale.adapter.in.rest.api.model.SalesOrderLineResponse;
import com.example.inventory.wholesale.adapter.in.rest.api.model.SalesOrderResponse;
import com.example.inventory.wholesale.application.port.in.CancelSalesOrderUseCase;
import com.example.inventory.wholesale.application.port.in.GetSalesOrderUseCase;
import com.example.inventory.wholesale.application.port.in.PlaceSalesOrderUseCase;
import com.example.inventory.wholesale.application.port.in.ShipSalesOrderUseCase;
import com.example.inventory.wholesale.domain.model.Order;

/** SalesOrder REST 入力アダプタ。OpenAPI 生成 {@link SalesOrdersApi} を実装(ADR-0006)。 */
@RestController
public class SalesOrderController implements SalesOrdersApi {

    private final PlaceSalesOrderUseCase placeOrder;
    private final ShipSalesOrderUseCase shipOrder;
    private final CancelSalesOrderUseCase cancelOrder;
    private final GetSalesOrderUseCase getOrder;

    public SalesOrderController(
            PlaceSalesOrderUseCase placeOrder,
            ShipSalesOrderUseCase shipOrder,
            CancelSalesOrderUseCase cancelOrder,
            GetSalesOrderUseCase getOrder) {
        this.placeOrder = placeOrder;
        this.shipOrder = shipOrder;
        this.cancelOrder = cancelOrder;
        this.getOrder = getOrder;
    }

    @Override
    public ResponseEntity<SalesOrderResponse> placeSalesOrder(PlaceSalesOrderRequest request) {
        var lines =
                request.getItems().stream()
                        .map(
                                l ->
                                        new PlaceSalesOrderUseCase.Command.Line(
                                                l.getSkuCode(), l.getLocationId(), l.getQuantity()))
                        .collect(Collectors.toList());
        Order created =
                placeOrder.place(
                        new PlaceSalesOrderUseCase.Command(
                                request.getCode(),
                                request.getPartnerCode(),
                                request.getCurrency(),
                                lines,
                                request.getRequestedDeliveryDate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @Override
    public ResponseEntity<SalesOrderResponse> getSalesOrder(Long orderId) {
        return ResponseEntity.ok(toResponse(getOrder.get(orderId)));
    }

    @Override
    public ResponseEntity<SalesOrderResponse> shipSalesOrder(Long orderId) {
        return ResponseEntity.ok(toResponse(shipOrder.ship(orderId)));
    }

    @Override
    public ResponseEntity<SalesOrderResponse> cancelSalesOrder(Long orderId) {
        return ResponseEntity.ok(toResponse(cancelOrder.cancel(orderId)));
    }

    private static SalesOrderResponse toResponse(Order order) {
        SalesOrderResponse r = new SalesOrderResponse();
        r.setId(order.id().value());
        r.setCode(order.code().value());
        r.setPartnerCode(order.partnerCode().value());
        r.setStatus(SalesOrderResponse.StatusEnum.valueOf(order.status().name()));
        r.setCurrency(order.currency());
        r.setTotalAmount(order.totalAmount().doubleValue());
        r.setRequestedDeliveryDate(order.requestedDeliveryDate());
        r.setItems(
                order.items().stream()
                        .map(
                                it -> {
                                    SalesOrderLineResponse line = new SalesOrderLineResponse();
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
