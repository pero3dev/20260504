package com.example.inventory.tpl.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.tpl.adapter.in.rest.api.StockMovementsApi;
import com.example.inventory.tpl.adapter.in.rest.api.model.PlanStockMovementRequest;
import com.example.inventory.tpl.adapter.in.rest.api.model.StockMovementResponse;
import com.example.inventory.tpl.application.port.in.GetStockMovementUseCase;
import com.example.inventory.tpl.application.port.in.PlanStockMovementUseCase;
import com.example.inventory.tpl.domain.model.MovementType;
import com.example.inventory.tpl.domain.model.StockMovement;

/** StockMovement REST 入力アダプタ。OpenAPI 生成 {@link StockMovementsApi} を実装(ADR-0006)。 */
@RestController
public class StockMovementController implements StockMovementsApi {

    private final PlanStockMovementUseCase planUseCase;
    private final GetStockMovementUseCase getUseCase;

    public StockMovementController(
            PlanStockMovementUseCase planUseCase, GetStockMovementUseCase getUseCase) {
        this.planUseCase = planUseCase;
        this.getUseCase = getUseCase;
    }

    @Override
    public ResponseEntity<StockMovementResponse> planStockMovement(
            PlanStockMovementRequest request) {
        StockMovement created =
                planUseCase.plan(
                        new PlanStockMovementUseCase.Command(
                                request.getCode(),
                                request.getPartnerCode(),
                                request.getSkuCode(),
                                request.getLocationId(),
                                MovementType.valueOf(request.getMovementType().getValue()),
                                request.getQuantity(),
                                request.getReferenceCode()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @Override
    public ResponseEntity<StockMovementResponse> getStockMovement(Long movementId) {
        return ResponseEntity.ok(toResponse(getUseCase.get(movementId)));
    }

    private static StockMovementResponse toResponse(StockMovement m) {
        StockMovementResponse r = new StockMovementResponse();
        r.setId(m.id().value());
        r.setCode(m.code().value());
        r.setPartnerCode(m.partnerCode());
        r.setSkuCode(m.skuCode());
        r.setLocationId(m.locationId());
        r.setMovementType(StockMovementResponse.MovementTypeEnum.valueOf(m.movementType().name()));
        r.setQuantity(m.quantity());
        r.setStatus(StockMovementResponse.StatusEnum.valueOf(m.status().name()));
        r.setReferenceCode(m.referenceCode());
        r.setVersion(m.version() + 1);
        return r;
    }
}
