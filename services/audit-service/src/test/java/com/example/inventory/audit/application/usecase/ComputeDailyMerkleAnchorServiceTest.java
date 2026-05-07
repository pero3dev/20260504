package com.example.inventory.audit.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

import com.example.inventory.audit.application.port.in.ComputeDailyMerkleAnchorUseCase;
import com.example.inventory.audit.application.port.out.AuditArchiveExporter;
import com.example.inventory.audit.application.port.out.AuditChainReader;
import com.example.inventory.audit.application.port.out.MerkleAnchorRepository;
import com.example.inventory.audit.application.port.out.MerkleTreeCalculator;
import com.example.inventory.audit.domain.model.AuditOutcome;
import com.example.inventory.audit.domain.model.AuditRecord;
import com.example.inventory.audit.domain.model.HashHex;
import com.example.inventory.audit.domain.model.MerkleAnchor;
import com.example.inventory.commons.tenant.TenantId;

class ComputeDailyMerkleAnchorServiceTest {

    private static final TenantId TENANT = new TenantId("tenant-1");
    private static final LocalDate DATE = LocalDate.of(2026, 5, 6);
    private static final HashHex H1 = new HashHex("1".repeat(64));
    private static final HashHex H2 = new HashHex("2".repeat(64));
    private static final HashHex MERKLE_ROOT = new HashHex("a".repeat(64));

    private AuditChainReader reader;
    private MerkleAnchorRepository anchorRepo;
    private MerkleTreeCalculator merkle;
    private ComputeDailyMerkleAnchorService service;

    @BeforeEach
    void setUp() {
        reader = Mockito.mock(AuditChainReader.class);
        anchorRepo = Mockito.mock(MerkleAnchorRepository.class);
        merkle = Mockito.mock(MerkleTreeCalculator.class);
        service = new ComputeDailyMerkleAnchorService(reader, anchorRepo, merkle, Optional.empty());
    }

    @Test
    void 既存_anchor_があれば_そのまま返して_計算もappendも呼ばない() {
        MerkleAnchor existing =
                new MerkleAnchor(
                        TENANT, DATE, H1, 5L, 10L, 14L, Instant.parse("2026-05-07T01:00:00Z"));
        when(anchorRepo.find(TENANT, DATE)).thenReturn(Optional.of(existing));

        ComputeDailyMerkleAnchorUseCase.Result result =
                service.compute(new ComputeDailyMerkleAnchorUseCase.Command(TENANT, DATE));

        assertThat(result.alreadyAnchored()).isTrue();
        assertThat(result.anchor()).isEqualTo(existing);
        verify(reader, never()).findByOccurredRange(any(), any(), any());
        verify(merkle, never()).root(any());
        verify(anchorRepo, never()).append(any());
    }

    @Test
    void 新規_anchor_計算は_対象期間のレコード_hash_を_Merkle_root_にして_append_する() {
        when(anchorRepo.find(TENANT, DATE)).thenReturn(Optional.empty());
        when(reader.findByOccurredRange(eq(TENANT), any(), any()))
                .thenReturn(List.of(record(10L, H1), record(14L, H2)));
        when(merkle.root(List.of(H1, H2))).thenReturn(MERKLE_ROOT);

        ComputeDailyMerkleAnchorUseCase.Result result =
                service.compute(new ComputeDailyMerkleAnchorUseCase.Command(TENANT, DATE));

        assertThat(result.alreadyAnchored()).isFalse();
        assertThat(result.anchor().rootHash()).isEqualTo(MERKLE_ROOT);
        assertThat(result.anchor().recordCount()).isEqualTo(2L);
        assertThat(result.anchor().firstSequence()).isEqualTo(10L);
        assertThat(result.anchor().lastSequence()).isEqualTo(14L);

        ArgumentCaptor<MerkleAnchor> captor = ArgumentCaptor.forClass(MerkleAnchor.class);
        verify(anchorRepo).append(captor.capture());
        assertThat(captor.getValue().rootHash()).isEqualTo(MERKLE_ROOT);
    }

    @Test
    void レコードゼロ件でも_anchor_は作成される_INITIAL_root_count_0() {
        when(anchorRepo.find(TENANT, DATE)).thenReturn(Optional.empty());
        when(reader.findByOccurredRange(eq(TENANT), any(), any())).thenReturn(List.of());
        when(merkle.root(List.of())).thenReturn(HashHex.INITIAL);

        ComputeDailyMerkleAnchorUseCase.Result result =
                service.compute(new ComputeDailyMerkleAnchorUseCase.Command(TENANT, DATE));

        assertThat(result.anchor().rootHash()).isEqualTo(HashHex.INITIAL);
        assertThat(result.anchor().recordCount()).isZero();
        assertThat(result.anchor().firstSequence()).isZero();
        assertThat(result.anchor().lastSequence()).isZero();
        verify(anchorRepo).append(any());
    }

    @Test
    void 並行_race_で_append_が_DuplicateKeyException_になったら_既存を返す() {
        when(anchorRepo.find(TENANT, DATE))
                .thenReturn(Optional.empty()) // 1 回目: 既存なし
                .thenReturn(Optional.of(racingAnchor())); // 2 回目: 並行 race の勝者
        when(reader.findByOccurredRange(eq(TENANT), any(), any()))
                .thenReturn(List.of(record(1L, H1)));
        when(merkle.root(any())).thenReturn(MERKLE_ROOT);
        Mockito.doThrow(new DuplicateKeyException("race")).when(anchorRepo).append(any());

        ComputeDailyMerkleAnchorUseCase.Result result =
                service.compute(new ComputeDailyMerkleAnchorUseCase.Command(TENANT, DATE));

        assertThat(result.alreadyAnchored()).isTrue();
        assertThat(result.existing()).isPresent();
    }

    private static AuditRecord record(long seq, HashHex hash) {
        return new AuditRecord(
                TENANT,
                seq,
                100L + seq,
                "ACTION",
                "Type",
                "id",
                "user",
                "tenant",
                AuditOutcome.SUCCESS,
                null,
                false,
                "{}",
                Instant.parse("2026-05-06T10:00:00Z"),
                HashHex.INITIAL,
                hash);
    }

    private static MerkleAnchor racingAnchor() {
        return new MerkleAnchor(
                TENANT, DATE, MERKLE_ROOT, 1L, 1L, 1L, Instant.parse("2026-05-07T01:00:00Z"));
    }

    @Test
    void archive_exporter_があれば_新規_anchor_計算後に_records_と_anchor_を_export_する() {
        AuditArchiveExporter exporter = Mockito.mock(AuditArchiveExporter.class);
        ComputeDailyMerkleAnchorService withExporter =
                new ComputeDailyMerkleAnchorService(
                        reader, anchorRepo, merkle, Optional.of(exporter));
        when(anchorRepo.find(TENANT, DATE)).thenReturn(Optional.empty());
        List<AuditRecord> records = List.of(record(10L, H1), record(14L, H2));
        when(reader.findByOccurredRange(eq(TENANT), any(), any())).thenReturn(records);
        when(merkle.root(List.of(H1, H2))).thenReturn(MERKLE_ROOT);

        withExporter.compute(new ComputeDailyMerkleAnchorUseCase.Command(TENANT, DATE));

        verify(exporter).exportRecords(TENANT, DATE, records);
        verify(exporter).exportAnchor(any(MerkleAnchor.class));
    }

    @Test
    void archive_exporter_の例外は_anchor_完了を妨げない_warn_のみ() {
        AuditArchiveExporter exporter = Mockito.mock(AuditArchiveExporter.class);
        ComputeDailyMerkleAnchorService withExporter =
                new ComputeDailyMerkleAnchorService(
                        reader, anchorRepo, merkle, Optional.of(exporter));
        when(anchorRepo.find(TENANT, DATE)).thenReturn(Optional.empty());
        when(reader.findByOccurredRange(eq(TENANT), any(), any()))
                .thenReturn(List.of(record(1L, H1)));
        when(merkle.root(any())).thenReturn(MERKLE_ROOT);
        Mockito.doThrow(new RuntimeException("S3 down"))
                .when(exporter)
                .exportRecords(any(), any(), any());

        ComputeDailyMerkleAnchorUseCase.Result result =
                withExporter.compute(new ComputeDailyMerkleAnchorUseCase.Command(TENANT, DATE));

        // anchor 自体は成功扱い(DB 側に append 済)
        assertThat(result.alreadyAnchored()).isFalse();
        assertThat(result.anchor().rootHash()).isEqualTo(MERKLE_ROOT);
    }

    @Test
    void archive_exporter_は_既存_anchor_の場合は_呼ばれない() {
        AuditArchiveExporter exporter = Mockito.mock(AuditArchiveExporter.class);
        ComputeDailyMerkleAnchorService withExporter =
                new ComputeDailyMerkleAnchorService(
                        reader, anchorRepo, merkle, Optional.of(exporter));
        MerkleAnchor existing =
                new MerkleAnchor(
                        TENANT, DATE, H1, 5L, 10L, 14L, Instant.parse("2026-05-07T01:00:00Z"));
        when(anchorRepo.find(TENANT, DATE)).thenReturn(Optional.of(existing));

        withExporter.compute(new ComputeDailyMerkleAnchorUseCase.Command(TENANT, DATE));

        verify(exporter, never()).exportRecords(any(), any(), any());
        verify(exporter, never()).exportAnchor(any());
    }
}
