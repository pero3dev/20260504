package com.example.inventory.audit.adapter.out.archive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.inventory.audit.application.port.out.AuditArchiveExporter;
import com.example.inventory.audit.config.ArchiveProperties;
import com.example.inventory.audit.domain.model.AuditRecord;
import com.example.inventory.audit.domain.model.MerkleAnchor;
import com.example.inventory.commons.tenant.TenantId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectLockLegalHoldStatus;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3 Object Lock(Compliance mode)向け {@link AuditArchiveExporter} 実装(ADR-0008)。
 *
 * <p>レコードは tenant × date 単位で 1 オブジェクト = JSON Lines + gzip。 anchor は単発 JSON。 partition は Athena
 * パーティション形式 {@code tenant=<id>/date=<yyyy-MM-dd>/} で書く。
 *
 * <p>Object Lock の retention(1 年)+ Compliance mode は **bucket 設定** で強制(infra/audit-s3 の bucket
 * policy 参照)。 本 adapter は PutObject 側で legal hold を立てない(=保持期限が経過すれば自動削除可能、 1 年保持の SLA に整合)。
 *
 * <p>{@link ArchiveProperties#enabled()} が false のときは Bean 生成されない({@link
 * com.example.inventory.audit.config.ArchiveConfiguration} 側で gating)。
 */
public class S3AuditArchiveExporter implements AuditArchiveExporter {

    private static final Logger LOG = LoggerFactory.getLogger(S3AuditArchiveExporter.class);

    private static final String CONTENT_TYPE_JSONL_GZIP = "application/gzip";
    private static final String CONTENT_TYPE_JSON = "application/json";

    private final S3Client s3;
    private final ArchiveProperties properties;
    private final ObjectMapper objectMapper;

    public S3AuditArchiveExporter(S3Client s3, ArchiveProperties properties) {
        this.s3 = s3;
        this.properties = properties;
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public String exportRecords(TenantId tenantId, LocalDate date, List<AuditRecord> records) {
        String key = recordsKey(tenantId, date);
        byte[] payload = serializeRecordsJsonlGzip(records);
        putObject(key, payload, CONTENT_TYPE_JSONL_GZIP);
        String uri = "s3://" + properties.bucket() + "/" + key;
        LOG.info(
                "audit records を S3 に export tenant={} date={} count={} uri={}",
                tenantId.value(),
                date,
                records.size(),
                uri);
        return uri;
    }

    @Override
    public String exportAnchor(MerkleAnchor anchor) {
        String key = anchorKey(anchor.tenantId(), anchor.anchorDate());
        byte[] payload = serializeAnchorJson(anchor);
        putObject(key, payload, CONTENT_TYPE_JSON);
        String uri = "s3://" + properties.bucket() + "/" + key;
        LOG.info(
                "anchor を S3 に export tenant={} date={} root={} uri={}",
                anchor.tenantId().value(),
                anchor.anchorDate(),
                anchor.rootHash().value(),
                uri);
        return uri;
    }

    private String recordsKey(TenantId tenantId, LocalDate date) {
        return joinPrefix(properties.recordsPrefix())
                + "tenant="
                + tenantId.value()
                + "/date="
                + date
                + "/records.jsonl.gz";
    }

    private String anchorKey(TenantId tenantId, LocalDate date) {
        return joinPrefix(properties.anchorsPrefix())
                + "tenant="
                + tenantId.value()
                + "/date="
                + date
                + "/anchor.json";
    }

    private static String joinPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return "";
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private byte[] serializeRecordsJsonlGzip(List<AuditRecord> records) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                GZIPOutputStream gz = new GZIPOutputStream(baos)) {
            for (AuditRecord r : records) {
                String line = objectMapper.writeValueAsString(toMap(r));
                gz.write(line.getBytes(StandardCharsets.UTF_8));
                gz.write('\n');
            }
            gz.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("audit records の JSON Lines 直列化に失敗", e);
        }
    }

    private byte[] serializeAnchorJson(MerkleAnchor anchor) {
        try {
            return objectMapper.writeValueAsBytes(toMap(anchor));
        } catch (IOException e) {
            throw new UncheckedIOException("anchor の JSON 直列化に失敗", e);
        }
    }

    /** 1 レコードを順序保持で Map 化(Jackson が record の component 順を保証しないため明示)。 */
    private static Map<String, Object> toMap(AuditRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenantId", r.tenantId().value());
        m.put("sequence", r.sequence());
        m.put("eventId", r.eventId());
        m.put("action", r.action());
        m.put("targetType", r.targetType());
        m.put("targetId", r.targetId());
        m.put("operatorUserId", r.operatorUserId());
        m.put("operatorTenantId", r.operatorTenantId());
        m.put("outcome", r.outcome().name());
        m.put("errorCode", r.errorCode());
        m.put("readOnly", r.readOnly());
        m.put("payloadJson", r.payloadJson());
        m.put("occurredAt", r.occurredAt().toString());
        m.put("prevHash", r.prevHash().value());
        m.put("hash", r.hash().value());
        return m;
    }

    private static Map<String, Object> toMap(MerkleAnchor a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenantId", a.tenantId().value());
        m.put("anchorDate", a.anchorDate().toString());
        m.put("rootHash", a.rootHash().value());
        m.put("recordCount", a.recordCount());
        m.put("firstSequence", a.firstSequence());
        m.put("lastSequence", a.lastSequence());
        m.put("computedAt", a.computedAt().toString());
        return m;
    }

    private void putObject(String key, byte[] payload, String contentType) {
        PutObjectRequest.Builder b =
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) payload.length)
                        // legal hold は付けない。 retention は bucket default(Compliance, 1 年)で強制される。
                        .objectLockLegalHoldStatus(ObjectLockLegalHoldStatus.OFF);
        s3.putObject(b.build(), RequestBody.fromBytes(payload));
    }
}
