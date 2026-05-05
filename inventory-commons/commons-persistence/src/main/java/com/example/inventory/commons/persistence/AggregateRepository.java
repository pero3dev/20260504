package com.example.inventory.commons.persistence;

import java.util.Optional;

/**
 * MyBatis 規約(ADR-0009)下での集約永続化テンプレート。
 *
 * <p>実装は各サービスの {@code adapter/out/persistence} パッケージに置く。 アプリケーション層はこのポートのみに依存する。
 *
 * @param <A> 集約ルート型
 * @param <I> 集約ID型
 */
public interface AggregateRepository<A, I> {

    Optional<A> findById(I id);

    /** 新規ならINSERT、既存なら楽観ロック検査付きでUPDATE。 影響行数が0だった場合、実装は {@link OptimisticLockException} を投げること。 */
    A save(A aggregate);

    void delete(A aggregate);
}
