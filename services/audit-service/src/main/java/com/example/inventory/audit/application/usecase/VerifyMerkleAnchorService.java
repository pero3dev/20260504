package com.example.inventory.audit.application.usecase;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.inventory.audit.application.port.in.VerifyMerkleAnchorUseCase;
import com.example.inventory.audit.application.port.out.AuditChainReader;
import com.example.inventory.audit.application.port.out.MerkleAnchorRepository;
import com.example.inventory.audit.application.port.out.MerkleTreeCalculator;
import com.example.inventory.audit.domain.model.AuditRecord;
import com.example.inventory.audit.domain.model.HashHex;
import com.example.inventory.audit.domain.model.MerkleAnchor;

/**
 * Merkle anchor 整合性検証サービス(ADR-0008)。
 *
 * <p>保存値と現時点の再計算値を比較。レコード件数 / Merkle root の差異を検出する。 主用途: 月次 compliance audit、incident response。
 *
 * <p>検出ステータス:
 *
 * <ul>
 *   <li>OK: 件数も root も一致
 *   <li>ROOT_MISMATCH: 件数は一致するが root が違う(改ざんの可能性)
 *   <li>RECORD_COUNT_MISMATCH: 件数が違う(後追い登録 or 削除の可能性)
 *   <li>ANCHOR_NOT_FOUND: anchor 自体が無い
 * </ul>
 */
@Service
public class VerifyMerkleAnchorService implements VerifyMerkleAnchorUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(VerifyMerkleAnchorService.class);

    private final MerkleAnchorRepository anchorRepository;
    private final AuditChainReader reader;
    private final MerkleTreeCalculator merkleCalculator;

    public VerifyMerkleAnchorService(
            MerkleAnchorRepository anchorRepository,
            AuditChainReader reader,
            MerkleTreeCalculator merkleCalculator) {
        this.anchorRepository = anchorRepository;
        this.reader = reader;
        this.merkleCalculator = merkleCalculator;
    }

    @Override
    public Report verify(Command command) {
        Optional<MerkleAnchor> existing =
                anchorRepository.find(command.tenantId(), command.anchorDate());
        if (existing.isEmpty()) {
            return new Report(
                    command.tenantId(),
                    command.anchorDate(),
                    Status.ANCHOR_NOT_FOUND,
                    Optional.empty(),
                    Optional.empty(),
                    0L);
        }

        Instant from = command.anchorDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = command.anchorDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<AuditRecord> records = reader.findByOccurredRange(command.tenantId(), from, to);

        long currentCount = records.size();
        if (currentCount != existing.get().recordCount()) {
            LOG.warn(
                    "anchor 件数不整合 tenant={} date={} expected={} actual={}",
                    command.tenantId().value(),
                    command.anchorDate(),
                    existing.get().recordCount(),
                    currentCount);
            return new Report(
                    command.tenantId(),
                    command.anchorDate(),
                    Status.RECORD_COUNT_MISMATCH,
                    existing,
                    Optional.empty(),
                    currentCount);
        }

        List<HashHex> leaves = records.stream().map(AuditRecord::hash).collect(Collectors.toList());
        HashHex recomputed = merkleCalculator.root(leaves);

        if (!recomputed.equals(existing.get().rootHash())) {
            LOG.warn(
                    "anchor root 不整合 tenant={} date={} stored={} recomputed={}",
                    command.tenantId().value(),
                    command.anchorDate(),
                    existing.get().rootHash().value(),
                    recomputed.value());
            return new Report(
                    command.tenantId(),
                    command.anchorDate(),
                    Status.ROOT_MISMATCH,
                    existing,
                    Optional.of(recomputed),
                    currentCount);
        }

        return new Report(
                command.tenantId(),
                command.anchorDate(),
                Status.OK,
                existing,
                Optional.of(recomputed),
                currentCount);
    }
}
