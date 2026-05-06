package com.example.inventory.audit.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.inventory.audit.domain.model.HashHex;

class Sha256MerkleTreeCalculatorTest {

    private final Sha256MerkleTreeCalculator calc = new Sha256MerkleTreeCalculator();

    @Test
    void 空入力は_INITIAL_全ゼロ_を返す() {
        assertThat(calc.root(List.of())).isEqualTo(HashHex.INITIAL);
        assertThat(calc.root(null)).isEqualTo(HashHex.INITIAL);
    }

    @Test
    void 単一要素は_その要素自身が_root() {
        HashHex h = hashOf("a");
        assertThat(calc.root(List.of(h))).isEqualTo(h);
    }

    @Test
    void 偶数_2要素_の_root_は_concat_の_SHA256_と一致する() {
        HashHex h1 = hashOf("a");
        HashHex h2 = hashOf("b");
        HashHex expected = sha256Hex(h1.value() + h2.value());

        assertThat(calc.root(List.of(h1, h2))).isEqualTo(expected);
    }

    @Test
    void 奇数_3要素_の_root_は_最終ノードを複製してペア化する() {
        HashHex h1 = hashOf("a");
        HashHex h2 = hashOf("b");
        HashHex h3 = hashOf("c");
        // L1: [SHA256(h1+h2), SHA256(h3+h3)]
        HashHex l10 = sha256Hex(h1.value() + h2.value());
        HashHex l11 = sha256Hex(h3.value() + h3.value());
        // root: SHA256(l10+l11)
        HashHex expected = sha256Hex(l10.value() + l11.value());

        assertThat(calc.root(List.of(h1, h2, h3))).isEqualTo(expected);
    }

    @Test
    void 完全二分木_4要素_の_root_は_左右で計算したノードを連結して一致する() {
        HashHex h1 = hashOf("a");
        HashHex h2 = hashOf("b");
        HashHex h3 = hashOf("c");
        HashHex h4 = hashOf("d");
        HashHex l10 = sha256Hex(h1.value() + h2.value());
        HashHex l11 = sha256Hex(h3.value() + h4.value());
        HashHex expected = sha256Hex(l10.value() + l11.value());

        assertThat(calc.root(List.of(h1, h2, h3, h4))).isEqualTo(expected);
    }

    @Test
    void 同じ葉でも順序が違えば_root_は変わる_順序保存性質() {
        HashHex h1 = hashOf("a");
        HashHex h2 = hashOf("b");
        HashHex r1 = calc.root(List.of(h1, h2));
        HashHex r2 = calc.root(List.of(h2, h1));

        assertThat(r1).isNotEqualTo(r2);
    }

    private static HashHex hashOf(String s) {
        return sha256Hex(s);
    }

    private static HashHex sha256Hex(String s) {
        try {
            byte[] d =
                    MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(d.length * 2);
            for (byte b : d) hex.append(String.format("%02x", b & 0xff));
            return new HashHex(hex.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
