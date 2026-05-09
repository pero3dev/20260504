package com.example.inventory.commons.test.arch.fixtures.application.port.out;

/**
 * {@code writePathsAreAuditable} の動作検証用 synthetic repository。 名前末尾が {@code Repository} で write
 * メソッド名 pattern に合致するため、 ルール内の判定で「リポジトリ書込」として認識される。
 */
public interface FakeRepository {
    void save(Object aggregate);

    void delete(Object aggregate);

    Object findById(long id);
}
