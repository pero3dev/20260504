package com.example.inventory.core.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.core.adapter.in.rest.api.ReservationsApi;
import com.example.inventory.core.adapter.in.rest.api.model.ReservationRequest;
import com.example.inventory.core.adapter.in.rest.api.model.ReservationResponse;
import com.example.inventory.core.application.port.in.ReserveInventoryCommand;
import com.example.inventory.core.application.port.in.ReserveInventoryUseCase;
import com.example.inventory.core.domain.model.ReservationId;

/**
 * REST 入力アダプタ。OpenAPI スキーマ(docs/openapi/inventory-core.yaml)から生成された {@link ReservationsApi}
 * を実装することで、スキーマと実装の乖離をコンパイル時に検出する (ADR-0006)。
 *
 * <p>路径・パラメータ名・DTO 型・ステータスコードはすべて生成インタフェース由来。 バリデーションは生成 DTO に付与されたアノテーション({@code @Min},
 * {@code @Max} 等)で動作する。
 *
 * <p>エラーは commons-error の {@code GlobalExceptionHandler} が捕捉して RFC 7807 ProblemDetail
 * に変換する。本コントローラ自体は例外を投げてよい。
 */
@RestController
public class ReservationController implements ReservationsApi {

    private final ReserveInventoryUseCase useCase;

    public ReservationController(ReserveInventoryUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public ResponseEntity<ReservationResponse> reserveInventory(
            Long inventoryId, ReservationRequest request) {
        ReservationId rid =
                useCase.reserve(new ReserveInventoryCommand(inventoryId, request.getQuantity()));
        ReservationResponse response = new ReservationResponse();
        response.setReservationId(rid.value());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
