package com.example.inventory.manufacturing.application.port.out;

import java.util.Optional;

import com.example.inventory.manufacturing.domain.model.Bom;

/**
 * BOM(製品 SKU 単位の構成)の参照ポート。
 *
 * <p>MVP は Read のみ。BOM の登録/改訂はマスタデータ運用の別フローで行う想定。
 */
public interface BomRepository {

    Optional<Bom> findByProductSkuCode(String productSkuCode);
}
