package com.example.inventory.core.application.port.in;

import com.example.inventory.core.domain.model.ReservationId;

/**
 * 入力ポート: 指定された在庫レコードに数量を引当てる。
 *
 * <p>adapter-in の実装(REST コントローラ、Kafka コマンドコンシューマ)は 外部リクエストを {@link ReserveInventoryCommand}
 * に変換してこのポートを呼び出す。 本ユースケースを介さずにドメイン操作を起動する経路は禁止する。
 */
public interface ReserveInventoryUseCase {

    ReservationId reserve(ReserveInventoryCommand command);
}
