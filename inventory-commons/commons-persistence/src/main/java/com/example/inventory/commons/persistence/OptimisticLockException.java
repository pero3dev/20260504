package com.example.inventory.commons.persistence;

/**
 * バージョン付きUPDATEで影響行数が0だった場合にスローされる。 集約の読み込み以降に他トランザクションが先に更新したことを意味する。 呼び出し側は集約を再読込してユースケースを再試行できる。
 */
public class OptimisticLockException extends RuntimeException {

    public OptimisticLockException(String aggregateType, Object id, long expectedVersion) {
        super("楽観ロック失敗: %s id=%s expectedVersion=%d".formatted(aggregateType, id, expectedVersion));
    }
}
