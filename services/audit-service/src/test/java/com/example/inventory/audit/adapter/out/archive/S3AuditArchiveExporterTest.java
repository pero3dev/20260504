package com.example.inventory.audit.adapter.out.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.example.inventory.audit.config.ArchiveProperties;
import com.example.inventory.audit.domain.model.AuditOutcome;
import com.example.inventory.audit.domain.model.AuditRecord;
import com.example.inventory.audit.domain.model.HashHex;
import com.example.inventory.audit.domain.model.MerkleAnchor;
import com.example.inventory.commons.tenant.TenantId;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectLockLegalHoldStatus;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3AuditArchiveExporterTest {

    private static final TenantId TENANT = new TenantId("tenant-1");
    private static final LocalDate DATE = LocalDate.of(2026, 5, 6);

    private S3Client s3;
    private S3AuditArchiveExporter exporter;

    @BeforeEach
    void setUp() {
        s3 = Mockito.mock(S3Client.class);
        ArchiveProperties props =
                new ArchiveProperties(
                        true,
                        "audit-bucket",
                        "ap-northeast-1",
                        null,
                        "audit-records",
                        "audit-anchors");
        exporter = new S3AuditArchiveExporter(s3, props);
    }

    @Test
    void exportRecords_は_jsonl_gzip_を_S3_に_PUT_して_URI_を_返す() throws IOException {
        AuditRecord r1 = record(10L, "1".repeat(64));
        AuditRecord r2 = record(11L, "2".repeat(64));

        String uri = exporter.exportRecords(TENANT, DATE, List.of(r1, r2));

        assertThat(uri)
                .isEqualTo(
                        "s3://audit-bucket/audit-records/tenant=tenant-1/date=2026-05-06/records.jsonl.gz");

        ArgumentCaptor<PutObjectRequest> reqCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(reqCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest req = reqCaptor.getValue();
        assertThat(req.bucket()).isEqualTo("audit-bucket");
        assertThat(req.key())
                .isEqualTo("audit-records/tenant=tenant-1/date=2026-05-06/records.jsonl.gz");
        assertThat(req.contentType()).isEqualTo("application/gzip");
        assertThat(req.objectLockLegalHoldStatus()).isEqualTo(ObjectLockLegalHoldStatus.OFF);

        // gzip を解凍して JSONL 行が 2 行 + 各行に sequence / hash が出ていることだけ確認(format 詳細は中身)
        byte[] gz = readAll(bodyCaptor.getValue());
        String jsonl = ungzip(gz);
        String[] lines = jsonl.split("\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0])
                .contains("\"sequence\":10")
                .contains("\"hash\":\"" + "1".repeat(64) + "\"");
        assertThat(lines[1])
                .contains("\"sequence\":11")
                .contains("\"hash\":\"" + "2".repeat(64) + "\"");
    }

    @Test
    void exportRecords_は_records_が_空でも_PUT_する_その日に何も無かったことの_WORM_証跡() throws IOException {
        String uri = exporter.exportRecords(TENANT, DATE, List.of());

        assertThat(uri).contains("records.jsonl.gz");
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(any(PutObjectRequest.class), bodyCaptor.capture());
        // 空でも gzip ヘッダ + footer は乗っている → 解凍結果は空文字列
        byte[] gz = readAll(bodyCaptor.getValue());
        assertThat(ungzip(gz)).isEmpty();
    }

    @Test
    void exportAnchor_は_JSON_を_S3_に_PUT_して_URI_を_返す() throws IOException {
        MerkleAnchor anchor =
                new MerkleAnchor(
                        TENANT,
                        DATE,
                        new HashHex("a".repeat(64)),
                        2L,
                        10L,
                        11L,
                        Instant.parse("2026-05-07T01:00:00Z"));

        String uri = exporter.exportAnchor(anchor);

        assertThat(uri)
                .isEqualTo(
                        "s3://audit-bucket/audit-anchors/tenant=tenant-1/date=2026-05-06/anchor.json");

        ArgumentCaptor<PutObjectRequest> reqCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(reqCaptor.capture(), bodyCaptor.capture());
        PutObjectRequest req = reqCaptor.getValue();
        assertThat(req.contentType()).isEqualTo("application/json");
        String json = new String(readAll(bodyCaptor.getValue()), StandardCharsets.UTF_8);
        assertThat(json).contains("\"rootHash\":\"" + "a".repeat(64) + "\"");
        assertThat(json).contains("\"recordCount\":2");
        assertThat(json).contains("\"firstSequence\":10");
        assertThat(json).contains("\"lastSequence\":11");
    }

    private static AuditRecord record(long seq, String hashHex) {
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
                new HashHex(hashHex));
    }

    private static byte[] readAll(RequestBody body) throws IOException {
        try (var in = body.contentStreamProvider().newStream()) {
            return in.readAllBytes();
        }
    }

    private static String ungzip(byte[] gz) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(gz));
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            gis.transferTo(baos);
            return baos.toString(StandardCharsets.UTF_8);
        }
    }
}
