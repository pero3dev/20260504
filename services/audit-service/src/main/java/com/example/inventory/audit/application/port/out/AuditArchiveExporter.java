package com.example.inventory.audit.application.port.out;

import java.time.LocalDate;
import java.util.List;

import com.example.inventory.audit.domain.model.AuditRecord;
import com.example.inventory.audit.domain.model.MerkleAnchor;
import com.example.inventory.commons.tenant.TenantId;

/**
 * 監査レコード + Merkle anchor を WORM ストレージ(本番は S3 Object Lock Compliance mode)に export する out
 * port(ADR-0008)。
 *
 * <p>DB(audit_record / audit_merkle_anchor)+ DB レベル WORM トリガを 1 次防衛とし、 S3 Object Lock Compliance
 * mode を 2 次防衛として二重保管する。 AWS root user でも保持期限内は削除/改竄が不可能。
 *
 * <p>本 port を呼ぶ側は失敗時にも anchor を保持して再送できる設計を取る (export 失敗 = WORM 二重化未完了であって anchor 自体の整合性は失われない)。
 *
 * <p>format は MVP で JSON Lines + gzip(records)/ JSON(anchor)。 Parquet 化は phase 2 の選択肢として残す (Athena
 * は外部 JSON 表でクエリ可能、 ADR-0008 の Implementation status を参照)。
 */
public interface AuditArchiveExporter {

    /**
     * tenant × date に紐づく監査レコードを WORM に export。
     *
     * <p>レコードゼロ件のときも空オブジェクトを export する(その日何も発生していないことの WORM 証跡)。
     *
     * @return export 先の S3 URI(s3://bucket/prefix/...)
     */
    String exportRecords(TenantId tenantId, LocalDate date, List<AuditRecord> records);

    /**
     * Merkle anchor を WORM に export(DB の {@code audit_merkle_anchor} と二重保管)。
     *
     * @return export 先の S3 URI
     */
    String exportAnchor(MerkleAnchor anchor);
}
