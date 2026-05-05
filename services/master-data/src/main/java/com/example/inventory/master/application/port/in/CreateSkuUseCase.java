package com.example.inventory.master.application.port.in;

import com.example.inventory.master.domain.model.Sku;

public interface CreateSkuUseCase {

    Sku create(Command command);

    record Command(String code, String name, String description, String unitOfMeasure) {
        public Command {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("code is required");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
        }
    }
}
