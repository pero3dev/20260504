package com.example.inventory.wholesale.adapter.out.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PartnerPriceMapper {

    PartnerPriceRow findCurrent(
            @Param("partnerCode") String partnerCode,
            @Param("skuCode") String skuCode,
            @Param("priceTier") String priceTier);
}
