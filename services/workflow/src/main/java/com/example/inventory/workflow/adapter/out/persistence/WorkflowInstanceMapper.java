package com.example.inventory.workflow.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkflowInstanceMapper {

    WorkflowInstanceRow findById(@Param("id") long id);

    int insert(@Param("row") WorkflowInstanceRow row);

    int update(
            @Param("row") WorkflowInstanceRow row, @Param("expectedVersion") long expectedVersion);

    int delete(@Param("id") long id, @Param("expectedVersion") long expectedVersion);

    List<WorkflowStepRow> findStepsByInstanceId(@Param("instanceId") long instanceId);

    int insertStep(@Param("row") WorkflowStepRow row);

    int upsertStep(@Param("row") WorkflowStepRow row);

    int deleteStepsByInstanceId(@Param("instanceId") long instanceId);

    /** B2 SLA scheduler 用: STARTED 状態かつ started_at < cutoff の id を最大 limit 件返す。 */
    List<Long> findStartedIdsOlderThan(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
