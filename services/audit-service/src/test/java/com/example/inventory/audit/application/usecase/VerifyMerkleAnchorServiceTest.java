package com.example.inventory.audit.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.inventory.audit.application.port.in.VerifyMerkleAnchorUseCase;
import com.example.inventory.audit.application.port.in.VerifyMerkleAnchorUseCase.Status;
import com.example.inventory.audit.application.port.out.AuditChainReader;
import com.example.inventory.audit.application.port.out.MerkleAnchorRepository;
import com.example.inventory.audit.application.port.out.MerkleTreeCalculator;
import com.example.inventory.audit.domain.model.AuditOutcome;
import com.example.inventory.audit.domain.model.AuditRecord;
import com.example.inventory.audit.domain.model.HashHex;
import com.example.inventory.audit.domain.model.MerkleAnchor;
import com.example.inventory.commons.tenant.TenantId;

class VerifyMerkleAnchorServiceTest {

    private static final TenantId TENANT = new TenantId("tenant-1");
    private static final LocalDate DATE = LocalDate.of(2026, 5, 6);
    private static final HashHex H1 = new HashHex("1".repeat(64));
    private static final HashHex H2 = new HashHex("2".repeat(64));
    private static final HashHex GOOD_ROOT = new HashHex("a".repeat(64));
    private static final HashHex BAD_ROOT = new HashHex("b".repeat(64));

    private MerkleAnchorRepository anchorRepo;
    private AuditChainReader reader;
    private MerkleTreeCalculator merkle;
    private VerifyMerkleAnchorService service;

    @BeforeEach
    void setUp() {
        anchorRepo = Mockito.mock(MerkleAnchorRepository.class);
        reader = Mockito.mock(AuditChainReader.class);
        merkle = Mockito.mock(MerkleTreeCalculator.class);
        service = new VerifyMerkleAnchorService(anchorRepo, reader, merkle);
    }

    @Test
    void anchor_が無ければ_ANCHOR_NOT_FOUND() {
        when(anchorRepo.find(TENANT, DATE)).thenReturn(Optional.empty());

        VerifyMerkleAnchorUseCase.Report report =
                service.verify(new VerifyMerkleAnchorUseCase.Command(TENANT, DATE));

        assertThat(report.status()).isEqualTo(Status.ANCHOR_NOT_FOUND);
    }

    @Test
    void 件数も_root_も一致なら_OK() {
        when(anchorRepo.find(TENANT, DATE)).thenReturn(Optional.of(anchor(2L, GOOD_ROOT)));
        when(reader.findByOccurredRange(eq(TENANT), any(), any()))
                .thenReturn(List.of(record(1L, H1), record(2L, H2)));
        when(merkle.root(List.of(H1, H2))).thenReturn(GOOD_ROOT);

        VerifyMerkleAnchorUseCase.Report report =
                service.verify(new VerifyMerkleAnchorUseCase.Command(TENANT, DATE));

        assertThat(report.status()).isEqualTo(Status.OK);
        assertThat(report.recomputedRoot()).contains(GOOD_ROOT);
        assertThat(report.currentRecordCount()).isEqualTo(2L);
    }

    @Test
    void 件数は一致するが_root_が違えば_ROOT_MISMATCH_改ざんの可能性() {
        when(anchorRepo.find(TENANT, DATE)).thenReturn(Optional.of(anchor(2L, GOOD_ROOT)));
        when(reader.findByOccurredRange(eq(TENANT), any(), any()))
                .thenReturn(List.of(record(1L, H1), record(2L, H2)));
        when(merkle.root(List.of(H1, H2))).thenReturn(BAD_ROOT);

        VerifyMerkleAnchorUseCase.Report report =
                service.verify(new VerifyMerkleAnchorUseCase.Command(TENANT, DATE));

        assertThat(report.status()).isEqualTo(Status.ROOT_MISMATCH);
        assertThat(report.recomputedRoot()).contains(BAD_ROOT);
    }

    @Test
    void 件数が違えば_RECORD_COUNT_MISMATCH() {
        when(anchorRepo.find(TENANT, DATE)).thenReturn(Optional.of(anchor(5L, GOOD_ROOT)));
        when(reader.findByOccurredRange(eq(TENANT), any(), any()))
                .thenReturn(List.of(record(1L, H1), record(2L, H2)));

        VerifyMerkleAnchorUseCase.Report report =
                service.verify(new VerifyMerkleAnchorUseCase.Command(TENANT, DATE));

        assertThat(report.status()).isEqualTo(Status.RECORD_COUNT_MISMATCH);
        assertThat(report.currentRecordCount()).isEqualTo(2L);
    }

    private static MerkleAnchor anchor(long count, HashHex root) {
        return new MerkleAnchor(
                TENANT, DATE, root, count, 1L, count, Instant.parse("2026-05-07T01:00:00Z"));
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
}
