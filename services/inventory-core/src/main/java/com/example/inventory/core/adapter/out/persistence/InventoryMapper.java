package com.example.inventory.core.adapter.out.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis マッパーインタフェース。SQL は {@code src/main/resources/mapper/InventoryMapper.xml} に定義する。
 *
 * <p>テナントの {@code search_path} は commons-tenant の {@link
 * com.example.inventory.commons.tenant.TenantSearchPathInterceptor} が設定する。本SQLにスキーマ名を明示しないこと。
 */
@Mapper
public interface InventoryMapper {

    InventoryRow findById(@Param("id") long id);

    InventoryRow findBySkuAndLocation(
            @Param("skuId") String skuId, @Param("locationId") String locationId);

    int insert(@Param("row") InventoryRow row);

    /**
     * バージョン付き UPDATE。影響行数を返す。リポジトリ実装側で 0 行の場合は {@link
     * com.example.inventory.commons.persistence.OptimisticLockException} に変換すること。
     */
    int update(@Param("row") InventoryRow row, @Param("expectedVersion") long expectedVersion);

    int delete(@Param("id") long id, @Param("expectedVersion") long expectedVersion);
}
