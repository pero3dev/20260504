package com.example.inventory.notification.adapter.out.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationMapper {

    int existsByTriggeredEventId(@Param("eventId") long eventId);

    int insert(@Param("row") NotificationRow row);
}
