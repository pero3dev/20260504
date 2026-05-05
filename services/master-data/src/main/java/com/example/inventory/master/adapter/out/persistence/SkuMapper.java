package com.example.inventory.master.adapter.out.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SkuMapper {

    SkuRow findById(@Param("id") long id);

    int existsByCode(@Param("code") String code);

    int insert(@Param("row") SkuRow row);

    int update(@Param("row") SkuRow row, @Param("expectedVersion") long expectedVersion);

    int delete(@Param("id") long id, @Param("expectedVersion") long expectedVersion);
}
