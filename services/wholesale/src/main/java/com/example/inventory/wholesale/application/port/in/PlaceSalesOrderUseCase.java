package com.example.inventory.wholesale.application.port.in;

import java.time.LocalDate;
import java.util.List;

import com.example.inventory.wholesale.domain.model.Order;

/**
 * 受注確定ユースケース。
 *
 * <p>取引先別価格は明細で渡さず、サーバ側で {@code partnerCode + skuCode} から PartnerPrice を引いて埋める。 これにより「クライアントが好き勝手な
 * unit_price で発注して契約価格を抜け道にする」事態を防ぐ。
 */
public interface PlaceSalesOrderUseCase {

    Order place(Command command);

    record Command(
            String code,
            String partnerCode,
            String currency,
            List<Line> items,
            LocalDate requestedDeliveryDate) {

        public Command {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("code は必須");
            if (partnerCode == null || partnerCode.isBlank())
                throw new IllegalArgumentException("partnerCode は必須");
            if (currency == null || currency.length() != 3)
                throw new IllegalArgumentException("currency は 3 文字 ISO 4217");
            if (items == null || items.isEmpty())
                throw new IllegalArgumentException("items は 1 行以上必要");
        }

        public record Line(String skuCode, String locationId, int quantity) {
            public Line {
                if (skuCode == null || skuCode.isBlank())
                    throw new IllegalArgumentException("skuCode は必須");
                if (locationId == null || locationId.isBlank())
                    throw new IllegalArgumentException("locationId は必須");
                if (quantity <= 0) throw new IllegalArgumentException("quantity は正の値");
            }
        }
    }
}
