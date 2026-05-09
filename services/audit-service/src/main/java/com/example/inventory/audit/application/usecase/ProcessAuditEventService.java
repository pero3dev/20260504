package com.example.inventory.audit.application.usecase;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.audit.application.port.in.ProcessAuditEventUseCase;
import com.example.inventory.audit.application.port.out.AuditRecordRepository;
import com.example.inventory.audit.application.port.out.HashCalculator;
import com.example.inventory.audit.domain.model.AuditRecord;
import com.example.inventory.audit.domain.model.HashHex;
import com.example.inventory.commons.audit.AuditExempt;

/**
 * 1イベントを取り込み、ハッシュチェーンに追加する。
 *
 * <p>並行 consumer から同テナントに同時 append が来ても整合するよう、 トランザクション開始直後に Postgres advisory lock
 * を取得してから最新レコードを読む。
 *
 * <p>処理:
 *
 * <ol>
 *   <li>idempotency: event_id 既存ならスキップ
 *   <li>テナント advisory lock 取得
 *   <li>最新レコードを取得 → prev_hash と次 sequence を決定
 *   <li>SHA-256 計算 → 新レコード生成
 *   <li>append(同 event_id 重複は DuplicateKeyException → 冪等にスキップ)
 * </ol>
 */
@Service
public class ProcessAuditEventService implements ProcessAuditEventUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessAuditEventService.class);

    private final AuditRecordRepository repository;
    private final HashCalculator hashCalculator;

    public ProcessAuditEventService(
            AuditRecordRepository repository, HashCalculator hashCalculator) {
        this.repository = repository;
        this.hashCalculator = hashCalculator;
    }

    @Override
    @Transactional
    @AuditExempt(reason = "audit emitter 自身。 ここで @Auditable を付けると audit 発行 → audit を生む再帰ループ")
    public Result process(Command command) {
        if (repository.existsByEventId(command.eventId())) {
            LOG.debug("既処理イベントをスキップ event_id={}", command.eventId());
            return Result.DUPLICATE_SKIPPED;
        }

        repository.acquireTenantLock(command.tenantId());

        Optional<AuditRecord> latest = repository.findLatest(command.tenantId());
        HashHex prevHash = latest.map(AuditRecord::hash).orElse(HashHex.INITIAL);
        long sequence = latest.map(r -> r.sequence() + 1).orElse(1L);

        HashHex hash = hashCalculator.compute(prevHash, command);

        AuditRecord newRecord =
                new AuditRecord(
                        command.tenantId(),
                        sequence,
                        command.eventId(),
                        command.action(),
                        command.targetType(),
                        command.targetId(),
                        command.operatorUserId(),
                        command.operatorTenantId(),
                        command.outcome(),
                        command.errorCode(),
                        command.readOnly(),
                        command.payloadJson(),
                        command.occurredAt(),
                        prevHash,
                        hash);

        try {
            repository.append(newRecord);
            LOG.debug(
                    "audit append tenant={} sequence={} event_id={}",
                    command.tenantId().value(),
                    sequence,
                    command.eventId());
            return Result.APPENDED;
        } catch (DuplicateKeyException e) {
            // existsByEventId 実行と append 実行の間に他 consumer が同 event_id を入れた稀なケース。
            // 冪等にスキップ。
            LOG.debug("レース条件で event_id={} が既に挿入済み、スキップ", command.eventId());
            return Result.DUPLICATE_SKIPPED;
        }
    }
}
