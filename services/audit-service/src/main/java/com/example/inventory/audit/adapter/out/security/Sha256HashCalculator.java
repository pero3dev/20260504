package com.example.inventory.audit.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Component;

import com.example.inventory.audit.application.port.in.ProcessAuditEventUseCase;
import com.example.inventory.audit.application.port.out.HashCalculator;
import com.example.inventory.audit.domain.model.HashHex;

/**
 * SHA-256 + 固定順序の正規化文字列でチェーンハッシュを計算する({@link HashCalculator} 実装)。
 *
 * <p>正規化フォーマット(全フィールドを区切り文字 {@code ""} (Unit Separator) で連結):
 *
 * <pre>
 *   prev_hash | event_id | tenant_id | action | target_type | target_id |
 *   operator_user_id | operator_tenant_id | outcome | error_code | read_only |
 *   payload_json | occurred_at(ISO-8601 nanos)
 * </pre>
 *
 * 区切りには制御文字を使い、業務文字列との衝突を排除する。null は空文字に正規化する (チェーン整合性検証ジョブも同じロジックを使うため、本実装の変更は破壊的)。
 */
@Component
public class Sha256HashCalculator implements HashCalculator {

    private static final char SEP = '';

    @Override
    public HashHex compute(HashHex prevHash, ProcessAuditEventUseCase.Command command) {
        StringBuilder sb = new StringBuilder(512);
        sb.append(prevHash.value()).append(SEP);
        sb.append(command.eventId()).append(SEP);
        sb.append(command.tenantId().value()).append(SEP);
        sb.append(orEmpty(command.action())).append(SEP);
        sb.append(orEmpty(command.targetType())).append(SEP);
        sb.append(orEmpty(command.targetId())).append(SEP);
        sb.append(orEmpty(command.operatorUserId())).append(SEP);
        sb.append(orEmpty(command.operatorTenantId())).append(SEP);
        sb.append(command.outcome().name()).append(SEP);
        sb.append(orEmpty(command.errorCode())).append(SEP);
        sb.append(command.readOnly()).append(SEP);
        sb.append(orEmpty(command.payloadJson())).append(SEP);
        sb.append(command.occurredAt() == null ? "" : command.occurredAt().toString());

        byte[] digest = sha256(sb.toString().getBytes(StandardCharsets.UTF_8));
        return new HashHex(toHex(digest));
    }

    private static byte[] sha256(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 が利用できない実行環境", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
