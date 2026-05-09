package com.example.inventory.identity.adapter.out.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TenantMembershipMapper {

    List<TenantMembershipRow> findByUserId(@Param("userId") long userId);

    TenantMembershipRow findByUserAndTenant(
            @Param("userId") long userId, @Param("tenantId") String tenantId);

    /**
     * tenant_memberships INSERT。 テーブル PK の {@code id} は Java domain では使わないので、 採番済の Snowflake ID を別
     * param で受ける(repository impl で生成)。
     */
    void insert(@Param("id") long id, @Param("row") TenantMembershipRow row);
}
