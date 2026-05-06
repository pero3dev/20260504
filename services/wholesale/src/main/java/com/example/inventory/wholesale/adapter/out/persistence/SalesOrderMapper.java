package com.example.inventory.wholesale.adapter.out.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SalesOrderMapper {

    SalesOrderRow findById(@Param("id") long id);

    int existsByCode(@Param("code") String code);

    int insert(@Param("row") SalesOrderRow row);

    int update(@Param("row") SalesOrderRow row, @Param("expectedVersion") long expectedVersion);

    int delete(@Param("id") long id, @Param("expectedVersion") long expectedVersion);

    List<SalesOrderItemRow> findItemsByOrderId(@Param("orderId") long orderId);

    int insertItem(@Param("row") SalesOrderItemRow row);

    int deleteItemsByOrderId(@Param("orderId") long orderId);
}
