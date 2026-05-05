package com.example.inventory.tpl.application.port.in;

import com.example.inventory.tpl.domain.model.MovementType;
import com.example.inventory.tpl.domain.model.StockMovement;

public interface PlanStockMovementUseCase {

    StockMovement plan(Command command);

    record Command(
            String code,
            String partnerCode,
            String skuCode,
            String locationId,
            MovementType movementType,
            int quantity,
            String referenceCode) {

        public Command {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("code は必須");
            if (partnerCode == null || partnerCode.isBlank())
                throw new IllegalArgumentException("partnerCode は必須");
            if (skuCode == null || skuCode.isBlank())
                throw new IllegalArgumentException("skuCode は必須");
            if (locationId == null || locationId.isBlank())
                throw new IllegalArgumentException("locationId は必須");
            if (movementType == null) throw new IllegalArgumentException("movementType は必須");
            if (quantity <= 0) throw new IllegalArgumentException("quantity は正の値");
        }
    }
}
