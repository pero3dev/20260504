package com.example.inventory.analytics.adapter.out.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProcessedEventMapper {

    int existsByEventId(@Param("eventId") long eventId);

    int insert(
            @Param("eventId") long eventId,
            @Param("tenantId") String tenantId,
            @Param("topic") String topic);
}
