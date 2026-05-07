package com.example.inventory.workflow.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.workflow.application.port.out.WorkflowInstanceRepository;
import com.example.inventory.workflow.domain.definition.WorkflowDefinition;
import com.example.inventory.workflow.domain.definition.WorkflowDefinitionRegistry;
import com.example.inventory.workflow.domain.model.DefinitionKey;
import com.example.inventory.workflow.domain.model.WorkflowInstance;
import com.example.inventory.workflow.domain.model.WorkflowInstanceId;
import com.example.inventory.workflow.domain.model.WorkflowStatus;

class ExpireOverdueWorkflowsServiceTest {

    private static final TenantId TENANT = new TenantId("tenant-1");
    private static final List<String> STEPS = List.of("VALIDATE", "APPROVE", "NOTIFY");

    private WorkflowInstanceRepository repository;
    private WorkflowDefinitionRegistry registry;
    private PlatformTransactionManager txManager;
    private ExpireOverdueWorkflowsService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(WorkflowInstanceRepository.class);
        registry = Mockito.mock(WorkflowDefinitionRegistry.class);
        txManager = Mockito.mock(PlatformTransactionManager.class);
        // TransactionTemplate が呼ぶ getTransaction → ダミー status を返す
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        // commit / rollback は no-op で OK(本体ロジックは expireIfOverdue / save の確認)
        doAnswer(inv -> null).when(txManager).commit(any());
        doAnswer(inv -> null).when(txManager).rollback(any());

        service = new ExpireOverdueWorkflowsService(repository, registry, txManager);

        // ApprovalFlow を 24h SLA としてレジストリに登録
        WorkflowDefinition def = Mockito.mock(WorkflowDefinition.class);
        when(def.key()).thenReturn(DefinitionKey.APPROVAL_FLOW);
        when(def.instanceSla()).thenReturn(Duration.ofHours(24));
        when(registry.find(DefinitionKey.APPROVAL_FLOW)).thenReturn(Optional.of(def));
    }

    @Test
    void 候補ゼロなら_repository_save_は呼ばれず_0_を返す() {
        when(repository.findStartedInstanceIdsOlderThan(any(), anyInt())).thenReturn(List.of());

        int expired = service.expireOverdue();

        assertThat(expired).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void SLA_超過した候補は_FAILED_に遷移して_save_される() {
        WorkflowInstance overdue = newStarted();
        // 25 時間前に startedAt を強制(restore で setup)
        Instant longAgo = Instant.now().minus(Duration.ofHours(25));
        WorkflowInstance overdueRestored =
                WorkflowInstance.restore(
                        new WorkflowInstanceId(1L),
                        TENANT,
                        DefinitionKey.APPROVAL_FLOW,
                        "biz-1",
                        "{}",
                        overdue.steps(),
                        1,
                        WorkflowStatus.STARTED,
                        null,
                        1L,
                        longAgo,
                        null);
        when(repository.findStartedInstanceIdsOlderThan(any(), anyInt()))
                .thenReturn(List.of(new WorkflowInstanceId(1L)));
        when(repository.findById(new WorkflowInstanceId(1L)))
                .thenReturn(Optional.of(overdueRestored));
        when(repository.save(any(WorkflowInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        int expired = service.expireOverdue();

        assertThat(expired).isEqualTo(1);
        verify(repository).save(any(WorkflowInstance.class));
        assertThat(overdueRestored.status()).isEqualTo(WorkflowStatus.FAILED);
    }

    @Test
    void 候補が_findById_で_empty_でも_他に波及せず_スキップ() {
        when(repository.findStartedInstanceIdsOlderThan(any(), anyInt()))
                .thenReturn(List.of(new WorkflowInstanceId(1L)));
        when(repository.findById(new WorkflowInstanceId(1L))).thenReturn(Optional.empty());

        int expired = service.expireOverdue();

        assertThat(expired).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void definition_が_registry_に無い場合は_スキップ_save_されない() {
        WorkflowInstance overdue = newStarted();
        Instant longAgo = Instant.now().minus(Duration.ofHours(25));
        WorkflowInstance overdueRestored =
                WorkflowInstance.restore(
                        new WorkflowInstanceId(1L),
                        TENANT,
                        DefinitionKey.APPROVAL_FLOW,
                        "biz-1",
                        "{}",
                        overdue.steps(),
                        1,
                        WorkflowStatus.STARTED,
                        null,
                        1L,
                        longAgo,
                        null);
        when(registry.find(DefinitionKey.APPROVAL_FLOW)).thenReturn(Optional.empty());
        when(repository.findStartedInstanceIdsOlderThan(any(), anyInt()))
                .thenReturn(List.of(new WorkflowInstanceId(1L)));
        when(repository.findById(new WorkflowInstanceId(1L)))
                .thenReturn(Optional.of(overdueRestored));

        int expired = service.expireOverdue();

        assertThat(expired).isZero();
        verify(repository, never()).save(any());
    }

    private static WorkflowInstance newStarted() {
        return WorkflowInstance.start(
                new WorkflowInstanceId(1L),
                TENANT,
                DefinitionKey.APPROVAL_FLOW,
                "biz-1",
                "{}",
                STEPS);
    }
}
