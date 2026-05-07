package com.example.inventory.audit.application.usecase;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.audit.application.port.in.ComputeDailyMerkleAnchorUseCase;
import com.example.inventory.audit.application.port.out.AuditArchiveExporter;
import com.example.inventory.audit.application.port.out.AuditChainReader;
import com.example.inventory.audit.application.port.out.MerkleAnchorRepository;
import com.example.inventory.audit.application.port.out.MerkleTreeCalculator;
import com.example.inventory.audit.domain.model.AuditRecord;
import com.example.inventory.audit.domain.model.HashHex;
import com.example.inventory.audit.domain.model.MerkleAnchor;

/**
 * 日次 Merkle anchor 計算サービス(ADR-0008)。
 *
 * <p>処理:
 *
 * <ol>
 *   <li>既存 anchor がある → そのまま返す(WORM 保護、再計算は VerifyMerkleAnchorService 経由で差異検出)
 *   <li>対象期間 = anchorDate UTC 00:00:00 〜 翌日 00:00:00
 *   <li>レコード群を取得 → 各 hash を葉として Merkle root 計算
 *   <li>新規 anchor を append
 * </ol>
 *
 * <p>レコードゼロ件でも anchor は作成する(rootHash = INITIAL、recordCount = 0)。 「その日には監査イベントが無かった」ことの WORM 証跡。
 */
@Service
public class ComputeDailyMerkleAnchorService implements ComputeDailyMerkleAnchorUseCase {

    private static final Logger LOG =
            LoggerFactory.getLogger(ComputeDailyMerkleAnchorService.class);

    private final AuditChainReader reader;
    private final MerkleAnchorRepository anchorRepository;
    private final MerkleTreeCalculator merkleCalculator;

    /**
     * S3 export は {@code platform.audit.archive.enabled=true} の時のみ Bean が存在するため Optional 注入。
     * 無効環境(test/local default)では DB 内 anchor のみが残る挙動を維持。
     */
    private final Optional<AuditArchiveExporter> archiveExporter;

    public ComputeDailyMerkleAnchorService(
            AuditChainReader reader,
            MerkleAnchorRepository anchorRepository,
            MerkleTreeCalculator merkleCalculator,
            Optional<AuditArchiveExporter> archiveExporter) {
        this.reader = reader;
        this.anchorRepository = anchorRepository;
        this.merkleCalculator = merkleCalculator;
        this.archiveExporter = archiveExporter;
    }

    @Override
    @Transactional
    public Result compute(Command command) {
        Optional<MerkleAnchor> existing =
                anchorRepository.find(command.tenantId(), command.anchorDate());
        if (existing.isPresent()) {
            LOG.debug(
                    "anchor 既存をそのまま返す tenant={} date={}",
                    command.tenantId().value(),
                    command.anchorDate());
            return new Result(existing.get(), true, existing);
        }

        Instant from = command.anchorDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = command.anchorDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<AuditRecord> records = reader.findByOccurredRange(command.tenantId(), from, to);

        List<HashHex> leaves = records.stream().map(AuditRecord::hash).collect(Collectors.toList());
        HashHex rootHash = merkleCalculator.root(leaves);

        long firstSeq = records.isEmpty() ? 0L : records.get(0).sequence();
        long lastSeq = records.isEmpty() ? 0L : records.get(records.size() - 1).sequence();

        MerkleAnchor anchor =
                new MerkleAnchor(
                        command.tenantId(),
                        command.anchorDate(),
                        rootHash,
                        records.size(),
                        firstSeq,
                        lastSeq,
                        Instant.now());

        try {
            anchorRepository.append(anchor);
            LOG.info(
                    "anchor 計算完了 tenant={} date={} root={} count={}",
                    command.tenantId().value(),
                    command.anchorDate(),
                    rootHash.value(),
                    records.size());
            exportToArchive(anchor, records);
            return new Result(anchor, false, Optional.empty());
        } catch (DuplicateKeyException e) {
            // 並行スケジューラ等で同時 append が走った稀なケース。既存を返す。
            Optional<MerkleAnchor> raceWinner =
                    anchorRepository.find(command.tenantId(), command.anchorDate());
            return new Result(raceWinner.orElse(anchor), true, raceWinner);
        }
    }

    /**
     * 新規 anchor のみ S3 に export(records + anchor)。 export の失敗は anchor 自体の整合性を脅かさないので warn ログのみ。 次回手動
     * anchor API({@code POST /admin/audit-chain/anchor})か運用 retry で再投入できる。
     */
    private void exportToArchive(MerkleAnchor anchor, List<AuditRecord> records) {
        if (archiveExporter.isEmpty()) return;
        AuditArchiveExporter exporter = archiveExporter.get();
        try {
            exporter.exportRecords(anchor.tenantId(), anchor.anchorDate(), records);
            exporter.exportAnchor(anchor);
        } catch (RuntimeException e) {
            // S3 投入失敗は DB 側の anchor を妨げない設計(WORM 二次防衛が遅れるだけ)。
            LOG.warn(
                    "S3 archive export 失敗 tenant={} date={} root={}: {}",
                    anchor.tenantId().value(),
                    anchor.anchorDate(),
                    anchor.rootHash().value(),
                    e.toString());
        }
    }
}
