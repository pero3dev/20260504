package com.example.inventory.identity.adapter.out.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TenantMembershipMapper {

    List<TenantMembershipRow> findByUserId(@Param("userId") long userId);

    TenantMembershipRow findByUserAndTenant(
            @Param("userId") long userId, @Param("tenantId") String tenantId);
}
