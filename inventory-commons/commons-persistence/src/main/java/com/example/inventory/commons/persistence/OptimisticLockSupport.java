package com.example.inventory.commons.persistence;

/**
 * リポジトリ実装の {@code save} がバージョン付きUPDATE を実行した直後に呼び出すヘルパ。 「影響行数 0 ⇒ {@link
 * OptimisticLockException}」の判定を共通化し、 13サービスで再実装させない。
 */
public final class OptimisticLockSupport {

    private OptimisticLockSupport() {}

    public static void verify(
            int rowsAffected, String aggregateType, Object id, long expectedVersion) {
        if (rowsAffected == 0) {
            throw new OptimisticLockException(aggregateType, id, expectedVersion);
        }
        if (rowsAffected > 1) {
            throw new IllegalStateException(
                    "バージョン付きUPDATEが %d 行に影響しました。1行のみが期待値です(集約=%s id=%s)。"
                            .formatted(rowsAffected, aggregateType, id));
        }
    }
}
