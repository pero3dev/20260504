package com.example.inventory.manufacturing.application.port.in;

import java.time.LocalDate;

import com.example.inventory.manufacturing.domain.model.WorkOrder;

/**
 * 製造指図計画ユースケース。
 *
 * <p>業態固有のポイント: 構成要素はクライアントから受け取らず、サーバ側で {@code productSkuCode} から BOM を引いてスナップショットする。これにより BOM
 * 改訂前に並走する指図が改訂後構成で動く事故を防ぐ。
 *
 * <p>状態は PLANNED で作成。Kafka 発行は無し(release 時に行う)。
 */
public interface PlaceWorkOrderUseCase {

    WorkOrder place(Command command);

    record Command(
            String code,
            String productSkuCode,
            String locationId,
            int plannedQuantity,
            LocalDate plannedStartDate) {

        public Command {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("code は必須");
            if (productSkuCode == null || productSkuCode.isBlank())
                throw new IllegalArgumentException("productSkuCode は必須");
            if (locationId == null || locationId.isBlank())
                throw new IllegalArgumentException("locationId は必須");
            if (plannedQuantity <= 0) throw new IllegalArgumentException("plannedQuantity は正の値");
        }
    }
}
