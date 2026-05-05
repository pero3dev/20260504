package com.example.inventory.master.application.port.out;

import java.util.Optional;

import com.example.inventory.commons.persistence.AggregateRepository;
import com.example.inventory.master.domain.model.Sku;
import com.example.inventory.master.domain.model.SkuCode;
import com.example.inventory.master.domain.model.SkuId;

public interface SkuRepository extends AggregateRepository<Sku, SkuId> {

    @Override
    Optional<Sku> findById(SkuId id);

    @Override
    Sku save(Sku aggregate);

    @Override
    void delete(Sku aggregate);

    /** 同テナント内で SKU コードの重複検出に使用。 */
    boolean existsByCode(SkuCode code);
}
