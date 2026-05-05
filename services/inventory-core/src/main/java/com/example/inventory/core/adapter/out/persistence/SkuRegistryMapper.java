package com.example.inventory.core.adapter.out.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SkuRegistryMapper {

    int existsByCode(@Param("code") String code);

    /** PostgreSQL ON CONFLICT で冪等な投影更新を行う。古い version 受信時は上書きしない。 */
    void upsert(@Param("row") SkuRegistryRow row);
}
