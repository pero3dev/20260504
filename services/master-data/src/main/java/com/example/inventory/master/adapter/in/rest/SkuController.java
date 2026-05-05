package com.example.inventory.master.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.master.adapter.in.rest.api.SkusApi;
import com.example.inventory.master.adapter.in.rest.api.model.CreateSkuRequest;
import com.example.inventory.master.adapter.in.rest.api.model.SkuResponse;
import com.example.inventory.master.application.port.in.CreateSkuUseCase;
import com.example.inventory.master.application.port.in.GetSkuUseCase;
import com.example.inventory.master.domain.model.Sku;

/**
 * SKU REST 入力アダプタ。OpenAPI スキーマ(docs/openapi/master-data.yaml)から生成された {@link SkusApi}
 * を実装する(ADR-0006)。
 */
@RestController
public class SkuController implements SkusApi {

    private final CreateSkuUseCase createSku;
    private final GetSkuUseCase getSku;

    public SkuController(CreateSkuUseCase createSku, GetSkuUseCase getSku) {
        this.createSku = createSku;
        this.getSku = getSku;
    }

    @Override
    public ResponseEntity<SkuResponse> createSku(CreateSkuRequest request) {
        Sku created =
                createSku.create(
                        new CreateSkuUseCase.Command(
                                request.getCode(),
                                request.getName(),
                                request.getDescription(),
                                request.getUnitOfMeasure()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @Override
    public ResponseEntity<SkuResponse> getSku(Long skuId) {
        return ResponseEntity.ok(toResponse(getSku.get(skuId)));
    }

    private static SkuResponse toResponse(Sku sku) {
        SkuResponse r = new SkuResponse();
        r.setId(sku.id().value());
        r.setCode(sku.code().value());
        r.setName(sku.name());
        r.setDescription(sku.description());
        r.setUnitOfMeasure(sku.unitOfMeasure());
        r.setVersion(sku.version() + 1); // save 後の version を返す(create 時の post-state)
        return r;
    }
}
