package com.example.inventory.master.application.port.in;

import com.example.inventory.master.domain.model.Sku;

public interface GetSkuUseCase {

    Sku get(long skuId);
}
