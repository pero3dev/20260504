package com.example.inventory.identity.adapter.out.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TenantMapper {

    int insert(TenantRow row);

    int updateStatus(TenantRow row);

    TenantRow findById(@Param("tenantId") String tenantId);

    List<TenantRow> findAll();
}
