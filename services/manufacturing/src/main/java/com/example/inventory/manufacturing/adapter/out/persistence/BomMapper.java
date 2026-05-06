package com.example.inventory.manufacturing.adapter.out.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BomMapper {

    List<BomComponentRow> findComponents(@Param("productSkuCode") String productSkuCode);
}
