package com.example.inventory.audit.application.port.out;

import java.util.List;

import com.example.inventory.audit.domain.model.HashHex;

/**
 * Merkle root を計算するポート。
 *
 * <p>標準的な Merkle tree:
 *
 * <ul>
 *   <li>葉: 入力 {@code List<HashHex>} の各要素(順序保存)
 *   <li>内部ノード: 隣接 2 葉を連結した SHA-256(子のうちノード値が 64 hex の prevHash 形式 → 2 個の hex を concatenate して
 *       SHA-256)
 *   <li>奇数ノード: 末尾を複製してペアにする(Bitcoin と同じ規約)
 *   <li>空入力: {@link HashHex#INITIAL}(全ゼロ)を返す
 *   <li>1 要素: その要素自身が root
 * </ul>
 */
public interface MerkleTreeCalculator {

    HashHex root(List<HashHex> leaves);
}
