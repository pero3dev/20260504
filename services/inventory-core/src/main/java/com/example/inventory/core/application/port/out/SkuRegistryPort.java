package com.example.inventory.core.application.port.out;

import com.example.inventory.core.domain.model.SkuId;
import com.example.inventory.core.domain.model.SkuRegistration;

/**
 * Master Data 由来の SKU 投影テーブルへのアクセスポート。
 *
 * <p>使い分け:
 *
 * <ul>
 *   <li>{@link #exists(SkuId)} — 引当ユースケースが「未登録 SKU の引当」を弾くために呼ぶ。
 *   <li>{@link #upsert(SkuRegistration)} — Kafka 消費側が {@code master.product.v1} を投影する際に呼ぶ。 同一 code
 *       に対する再配信は version 比較で冪等化する。
 * </ul>
 */
public interface SkuRegistryPort {

    boolean exists(SkuId code);

    void upsert(SkuRegistration registration);
}
