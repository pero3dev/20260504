package com.example.inventory.wholesale.application.port.out;

import java.util.Optional;

import com.example.inventory.wholesale.domain.model.PartnerCode;
import com.example.inventory.wholesale.domain.model.PartnerPrice;

/**
 * 取引先別契約価格の参照ポート。
 *
 * <p>MVP は単一価格(STANDARD tier)のみ参照。 将来は価格階層・適用期間(valid_from/valid_to)を引数に追加する。
 */
public interface PartnerPriceRepository {

    Optional<PartnerPrice> findCurrent(PartnerCode partnerCode, String skuCode);
}
