package com.example.inventory.manufacturing.adapter.in.rest;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.manufacturing.adapter.in.rest.api.WorkOrdersApi;
import com.example.inventory.manufacturing.adapter.in.rest.api.model.PlaceWorkOrderRequest;
import com.example.inventory.manufacturing.adapter.in.rest.api.model.WorkOrderComponentResponse;
import com.example.inventory.manufacturing.adapter.in.rest.api.model.WorkOrderResponse;
import com.example.inventory.manufacturing.application.port.in.CompleteWorkOrderUseCase;
import com.example.inventory.manufacturing.application.port.in.GetWorkOrderUseCase;
import com.example.inventory.manufacturing.application.port.in.PlaceWorkOrderUseCase;
import com.example.inventory.manufacturing.application.port.in.ReleaseWorkOrderUseCase;
import com.example.inventory.manufacturing.domain.model.WorkOrder;

/** WorkOrder REST 入力アダプタ。OpenAPI 生成 {@link WorkOrdersApi} を実装(ADR-0006)。 */
@RestController
public class WorkOrderController implements WorkOrdersApi {

    private final PlaceWorkOrderUseCase placeWorkOrder;
    private final ReleaseWorkOrderUseCase releaseWorkOrder;
    private final CompleteWorkOrderUseCase completeWorkOrder;
    private final GetWorkOrderUseCase getWorkOrder;

    public WorkOrderController(
            PlaceWorkOrderUseCase placeWorkOrder,
            ReleaseWorkOrderUseCase releaseWorkOrder,
            CompleteWorkOrderUseCase completeWorkOrder,
            GetWorkOrderUseCase getWorkOrder) {
        this.placeWorkOrder = placeWorkOrder;
        this.releaseWorkOrder = releaseWorkOrder;
        this.completeWorkOrder = completeWorkOrder;
        this.getWorkOrder = getWorkOrder;
    }

    @Override
    public ResponseEntity<WorkOrderResponse> placeWorkOrder(PlaceWorkOrderRequest request) {
        WorkOrder created =
                placeWorkOrder.place(
                        new PlaceWorkOrderUseCase.Command(
                                request.getCode(),
                                request.getProductSkuCode(),
                                request.getLocationId(),
                                request.getPlannedQuantity(),
                                request.getPlannedStartDate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @Override
    public ResponseEntity<WorkOrderResponse> releaseWorkOrder(Long workOrderId) {
        return ResponseEntity.ok(toResponse(releaseWorkOrder.release(workOrderId)));
    }

    @Override
    public ResponseEntity<WorkOrderResponse> completeWorkOrder(Long workOrderId) {
        return ResponseEntity.ok(toResponse(completeWorkOrder.complete(workOrderId)));
    }

    @Override
    public ResponseEntity<WorkOrderResponse> getWorkOrder(Long workOrderId) {
        return ResponseEntity.ok(toResponse(getWorkOrder.get(workOrderId)));
    }

    private static WorkOrderResponse toResponse(WorkOrder w) {
        WorkOrderResponse r = new WorkOrderResponse();
        r.setId(w.id().value());
        r.setCode(w.code().value());
        r.setProductSkuCode(w.productSkuCode());
        r.setLocationId(w.locationId());
        r.setPlannedQuantity(w.plannedQuantity());
        r.setStatus(WorkOrderResponse.StatusEnum.valueOf(w.status().name()));
        r.setPlannedStartDate(w.plannedStartDate());
        r.setComponents(
                w.components().stream()
                        .map(
                                c -> {
                                    WorkOrderComponentResponse cr =
                                            new WorkOrderComponentResponse();
                                    cr.setComponentSkuCode(c.componentSkuCode());
                                    cr.setQuantityPerUnit(c.quantityPerUnit());
                                    return cr;
                                })
                        .collect(Collectors.toList()));
        r.setVersion(w.version() + 1);
        return r;
    }
}
