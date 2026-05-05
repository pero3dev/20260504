package com.example.inventory.master.application.usecase;

import org.springframework.stereotype.Service;

import com.example.inventory.master.application.port.in.GetSkuUseCase;
import com.example.inventory.master.application.port.in.SkuNotFoundException;
import com.example.inventory.master.application.port.out.SkuRepository;
import com.example.inventory.master.domain.model.Sku;
import com.example.inventory.master.domain.model.SkuId;

@Service
public class GetSkuService implements GetSkuUseCase {

    private final SkuRepository repository;

    public GetSkuService(SkuRepository repository) {
        this.repository = repository;
    }

    @Override
    public Sku get(long skuId) {
        return repository
                .findById(new SkuId(skuId))
                .orElseThrow(() -> new SkuNotFoundException(skuId));
    }
}
