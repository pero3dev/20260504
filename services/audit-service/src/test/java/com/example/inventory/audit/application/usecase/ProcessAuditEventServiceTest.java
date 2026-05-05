package com.example.inventory.audit.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

import com.example.inventory.audit.application.port.in.ProcessAuditEventUseCase;
import com.example.inventory.audit.application.port.out.AuditRecordRepository;
import com.example.inventory.audit.application.port.out.HashCalculator;
import com.example.inventory.audit.domain.model.AuditOutcome;
import com.example.inventory.audit.domain.model.AuditRecord;
import com.example.inventory.audit.domain.model.HashHex;
import com.example.inventory.commons.tenant.TenantId;

class ProcessAuditEventServiceTest {

    private AuditRecordRepository repository;
    private HashCalculator calculator;
    private ProcessAuditEventService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(AuditRecordRepository.class);
        calculator = Mockito.mock(HashCalculator.class);
        service = new ProcessAuditEventService(repository, calculator);
    }

    @Test
    void 初回イベントはINITIALハッシュからチェーンを開始する() {
        when(repository.existsByEventId(anyLong())).thenReturn(false);
        when(repository.findLatest(any())).thenReturn(Optional.empty());
        HashHex newHash = sampleHash("aa");
        when(calculator.compute(eq(HashHex.INITIAL), any())).thenReturn(newHash);

        ProcessAuditEventUseCase.Result result = service.process(sampleCommand(1001L));

        assertThat(result).isEqualTo(ProcessAuditEventUseCase.Result.APPENDED);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(repository).append(captor.capture());
        AuditRecord saved = captor.getValue();
        assertThat(saved.sequence()).isEqualTo(1L);
        assertThat(saved.prevHash()).isEqualTo(HashHex.INITIAL);
        assertThat(saved.hash()).isEqualTo(newHash);
    }

    @Test
    void 二件目以降は前レコードのhashをprev_hashに使う() {
        AuditRecord previous =
                new AuditRecord(
                        new TenantId("dev"),
                        5L,
                        2000L,
                        "X",
                        "Y",
                        "z",
                        "u",
                        "dev",
                        AuditOutcome.SUCCESS,
                        null,
                        false,
                        "{}",
                        Instant.now(),
                        HashHex.INITIAL,
                        sampleHash("ab"));
        when(repository.existsByEventId(anyLong())).thenReturn(false);
        when(repository.findLatest(any())).thenReturn(Optional.of(previous));
        HashHex newHash = sampleHash("cd");
        when(calculator.compute(eq(previous.hash()), any())).thenReturn(newHash);

        ProcessAuditEventUseCase.Result result = service.process(sampleCommand(2001L));

        assertThat(result).isEqualTo(ProcessAuditEventUseCase.Result.APPENDED);
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(repository).append(captor.capture());
        AuditRecord saved = captor.getValue();
        assertThat(saved.sequence()).isEqualTo(6L);
        assertThat(saved.prevHash()).isEqualTo(previous.hash());
        assertThat(saved.hash()).isEqualTo(newHash);
    }

    @Test
    void 既処理event_idは冪等にスキップする() {
        when(repository.existsByEventId(eq(3001L))).thenReturn(true);

        ProcessAuditEventUseCase.Result result = service.process(sampleCommand(3001L));

        assertThat(result).isEqualTo(ProcessAuditEventUseCase.Result.DUPLICATE_SKIPPED);
        verify(repository, never()).acquireTenantLock(any());
        verify(repository, never()).append(any());
    }

    @Test
    void レース条件で重複INSERTになっても冪等にスキップする() {
        when(repository.existsByEventId(anyLong())).thenReturn(false);
        when(repository.findLatest(any())).thenReturn(Optional.empty());
        when(calculator.compute(any(), any())).thenReturn(sampleHash("ee"));
        doThrow(new DuplicateKeyException("event_id duplicate")).when(repository).append(any());

        ProcessAuditEventUseCase.Result result = service.process(sampleCommand(4001L));

        assertThat(result).isEqualTo(ProcessAuditEventUseCase.Result.DUPLICATE_SKIPPED);
    }

    @Test
    void 処理前にtenant_advisory_lockを取得する() {
        when(repository.existsByEventId(anyLong())).thenReturn(false);
        when(repository.findLatest(any())).thenReturn(Optional.empty());
        when(calculator.compute(any(), any())).thenReturn(sampleHash("ff"));

        service.process(sampleCommand(5001L));

        // existsByEventId → acquireTenantLock → findLatest → ... の順序を ordering で検証
        org.mockito.InOrder inOrder = Mockito.inOrder(repository, calculator);
        inOrder.verify(repository).existsByEventId(anyLong());
        inOrder.verify(repository).acquireTenantLock(eq(new TenantId("dev")));
        inOrder.verify(repository).findLatest(eq(new TenantId("dev")));
        inOrder.verify(calculator).compute(any(), any());
        inOrder.verify(repository).append(any());
    }

    private static ProcessAuditEventUseCase.Command sampleCommand(long eventId) {
        return new ProcessAuditEventUseCase.Command(
                new TenantId("dev"),
                eventId,
                "INVENTORY_RESERVE",
                "Inventory",
                "1",
                "user-001",
                "dev",
                AuditOutcome.SUCCESS,
                null,
                false,
                "{\"quantity\":3}",
                Instant.parse("2026-05-05T10:00:00Z"));
    }

    private static HashHex sampleHash(String prefix) {
        return new HashHex((prefix + "0".repeat(64 - prefix.length())));
    }
}
