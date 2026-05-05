package com.example.inventory.retail.application.port.in;

import java.math.BigDecimal;
import java.util.List;

import com.example.inventory.retail.domain.model.Order;

public interface PlaceOrderUseCase {

    Order place(Command command);

    record Command(String code, String customerEmail, String currency, List<Line> items) {

        public Command {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("code は必須");
            if (customerEmail == null || customerEmail.isBlank())
                throw new IllegalArgumentException("customerEmail は必須");
            if (currency == null || currency.length() != 3)
                throw new IllegalArgumentException("currency は 3 文字 ISO 4217");
            if (items == null || items.isEmpty())
                throw new IllegalArgumentException("items は 1 行以上必要");
        }

        public record Line(String skuCode, String locationId, int quantity, BigDecimal unitPrice) {
            public Line {
                if (skuCode == null || skuCode.isBlank())
                    throw new IllegalArgumentException("skuCode は必須");
                if (locationId == null || locationId.isBlank())
                    throw new IllegalArgumentException("locationId は必須");
                if (quantity <= 0) throw new IllegalArgumentException("quantity は正の値");
                if (unitPrice == null || unitPrice.signum() < 0)
                    throw new IllegalArgumentException("unitPrice は非負必須");
            }
        }
    }
}
