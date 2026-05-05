package com.example.inventory.retail.adapter.out.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.inventory.commons.event.OutboxRecord;

@Mapper
public interface OutboxMapper {

    int insert(@Param("rec") OutboxRecord record);

    List<OutboxRecord> pickUnpublished(@Param("batchSize") int batchSize);

    int markPublished(@Param("eventId") long eventId);
}
