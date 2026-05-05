package com.example.inventory.retail.adapter.out.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderMapper {

    OrderRow findById(@Param("id") long id);

    int existsByCode(@Param("code") String code);

    int insert(@Param("row") OrderRow row);

    int update(@Param("row") OrderRow row, @Param("expectedVersion") long expectedVersion);

    int delete(@Param("id") long id, @Param("expectedVersion") long expectedVersion);

    List<OrderItemRow> findItemsByOrderId(@Param("orderId") long orderId);

    int insertItem(@Param("row") OrderItemRow row);

    int deleteItemsByOrderId(@Param("orderId") long orderId);
}
