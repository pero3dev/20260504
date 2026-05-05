package com.example.inventory.tpl.adapter.out.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StockMovementMapper {

    StockMovementRow findById(@Param("id") long id);

    int existsByCode(@Param("code") String code);

    int insert(@Param("row") StockMovementRow row);

    int update(@Param("row") StockMovementRow row, @Param("expectedVersion") long expectedVersion);

    int delete(@Param("id") long id, @Param("expectedVersion") long expectedVersion);
}
