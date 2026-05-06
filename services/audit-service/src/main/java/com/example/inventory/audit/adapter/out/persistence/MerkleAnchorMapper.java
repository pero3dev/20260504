package com.example.inventory.audit.adapter.out.persistence;

import java.time.LocalDate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerkleAnchorMapper {

    MerkleAnchorRow find(
            @Param("tenantId") String tenantId, @Param("anchorDate") LocalDate anchorDate);

    int insert(@Param("row") MerkleAnchorRow row);
}
