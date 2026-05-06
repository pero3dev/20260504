package com.example.inventory.manufacturing.adapter.out.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkOrderMapper {

    WorkOrderRow findById(@Param("id") long id);

    int existsByCode(@Param("code") String code);

    int insert(@Param("row") WorkOrderRow row);

    int update(@Param("row") WorkOrderRow row, @Param("expectedVersion") long expectedVersion);

    int delete(@Param("id") long id, @Param("expectedVersion") long expectedVersion);

    List<WorkOrderComponentRow> findComponentsByWorkOrderId(@Param("workOrderId") long workOrderId);

    int insertComponent(@Param("row") WorkOrderComponentRow row);

    int deleteComponentsByWorkOrderId(@Param("workOrderId") long workOrderId);
}
