package com.example.inventory.audit.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.inventory.audit.application.port.out.MerkleTreeCalculator;
import com.example.inventory.audit.domain.model.HashHex;

/**
 * SHA-256 Merkle tree 実装(ADR-0008 の anchor 計算用)。
 *
 * <p>連結ルール: 2 つの子 hex を文字列連結(計 128 文字)し UTF-8 で SHA-256 → 64 hex の親に圧縮。 Bitcoin の Merkle
 * と同様、奇数ノードは末尾を複製。
 *
 * <p>本クラスのアルゴリズムは {@link com.example.inventory.audit.application.usecase.AuditChainVerifier}
 * と同じ「破壊的変更不可」扱い — anchor 値の互換性が崩れると過去の anchor が invalid になるため。
 */
@Component
public class Sha256MerkleTreeCalculator implements MerkleTreeCalculator {

    @Override
    public HashHex root(List<HashHex> leaves) {
        if (leaves == null || leaves.isEmpty()) {
            return HashHex.INITIAL;
        }
        if (leaves.size() == 1) {
            return leaves.get(0);
        }
        List<HashHex> level = new ArrayList<>(leaves);
        while (level.size() > 1) {
            List<HashHex> next = new ArrayList<>((level.size() + 1) / 2);
            for (int i = 0; i < level.size(); i += 2) {
                HashHex left = level.get(i);
                HashHex right = (i + 1 < level.size()) ? level.get(i + 1) : left; // 奇数なら左を複製
                next.add(combine(left, right));
            }
            level = next;
        }
        return level.get(0);
    }

    private static HashHex combine(HashHex left, HashHex right) {
        String concatHex = left.value() + right.value();
        byte[] digest = sha256(concatHex.getBytes(StandardCharsets.UTF_8));
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
}
