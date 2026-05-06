package com.example.inventory.wholesale.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.inventory.wholesale.application.port.out.PartnerPriceRepository;
import com.example.inventory.wholesale.domain.model.PartnerCode;
import com.example.inventory.wholesale.domain.model.PartnerPrice;

@Repository
public class PartnerPriceRepositoryImpl implements PartnerPriceRepository {

    private static final String DEFAULT_TIER = "STANDARD";

    private final PartnerPriceMapper mapper;

    public PartnerPriceRepositoryImpl(PartnerPriceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<PartnerPrice> findCurrent(PartnerCode partnerCode, String skuCode) {
        PartnerPriceRow row = mapper.findCurrent(partnerCode.value(), skuCode, DEFAULT_TIER);
        if (row == null) return Optional.empty();
        return Optional.of(
                new PartnerPrice(
                        new PartnerCode(row.partnerCode()),
                        row.skuCode(),
                        row.unitPrice(),
                        row.currency()));
    }
}
