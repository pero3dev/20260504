package com.example.inventory.core.adapter.out.persistence;

import org.springframework.stereotype.Repository;

import com.example.inventory.core.application.port.out.SkuRegistryPort;
import com.example.inventory.core.domain.model.SkuId;
import com.example.inventory.core.domain.model.SkuRegistration;

/** {@link SkuRegistryPort} の MyBatis 実装。 テナントスキーマへの search_path 切替は commons-tenant のインターセプタが担う。 */
@Repository
public class SkuRegistryRepositoryImpl implements SkuRegistryPort {

    private final SkuRegistryMapper mapper;

    public SkuRegistryRepositoryImpl(SkuRegistryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean exists(SkuId code) {
        return mapper.existsByCode(code.value()) > 0;
    }

    @Override
    public void upsert(SkuRegistration registration) {
        mapper.upsert(
                new SkuRegistryRow(
                        registration.code().value(),
                        registration.aggregateId(),
                        registration.name(),
                        registration.unitOfMeasure(),
                        registration.version()));
    }
}
