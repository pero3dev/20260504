package com.example.inventory.audit.application.port.out;

import com.example.inventory.audit.application.port.in.ProcessAuditEventUseCase;
import com.example.inventory.audit.domain.model.HashHex;

/**
 * SHA-256 ハッシュ計算ポート。{@code prev_hash + canonical(command)} の hex を返す。
 *
 * <p>正規化フォーマットは実装が定める(本MVPは固定順序のフィールド連結)。 チェーン整合性検証ジョブも同じロジックで再計算するため、変更は破壊的。
 */
public interface HashCalculator {

    HashHex compute(HashHex prevHash, ProcessAuditEventUseCase.Command command);
}
