package com.example.inventory.manufacturing.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.example.inventory.manufacturing.application.port.out.BomRepository;
import com.example.inventory.manufacturing.domain.model.Bom;
import com.example.inventory.manufacturing.domain.model.BomComponent;

@Repository
public class BomRepositoryImpl implements BomRepository {

    private final BomMapper mapper;

    public BomRepositoryImpl(BomMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Bom> findByProductSkuCode(String productSkuCode) {
        List<BomComponentRow> rows = mapper.findComponents(productSkuCode);
        if (rows == null || rows.isEmpty()) return Optional.empty();
        List<BomComponent> components =
                rows.stream()
                        .map(r -> new BomComponent(r.componentSkuCode(), r.quantityPerUnit()))
                        .collect(Collectors.toList());
        return Optional.of(new Bom(productSkuCode, components));
    }
}
