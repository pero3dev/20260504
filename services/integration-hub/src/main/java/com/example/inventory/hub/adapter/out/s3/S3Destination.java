package com.example.inventory.hub.adapter.out.s3;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.hub.application.port.out.OutboundDestination;
import com.example.inventory.hub.config.HubProperties;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3 への出力 destination(A3、 ADR-0007 follow-up)。
 *
 * <p>1 batch = 1 オブジェクト。 key は {@code <prefix>/<tenantId>/<yyyy-MM-dd>/<epoch-nano>-<rand>.csv} で
 * per-tenant 日次 partition 形式に揃え、 取引先側の検証 / 監査人 export を容易にする。
 *
 * <p>{@link #write} の単発書込はオブジェクト数が爆発するので **避ける**(default 実装でループ PUT する)。 標準は {@link #writeBatch}。
 */
public class S3Destination implements OutboundDestination {

    private static final Logger LOG = LoggerFactory.getLogger(S3Destination.class);
    private static final SecureRandom RAND = new SecureRandom();

    private final S3Client s3;
    private final HubProperties.S3Config config;

    public S3Destination(S3Client s3, HubProperties.S3Config config) {
        if (s3 == null) throw new IllegalArgumentException("S3Client は必須");
        if (config == null) throw new IllegalArgumentException("S3Config は必須");
        if (config.bucket() == null || config.bucket().isBlank()) {
            throw new IllegalArgumentException("S3Config.bucket は必須");
        }
        this.s3 = s3;
        this.config = config;
    }

    @Override
    public void write(TenantId tenantId, String formattedLine) {
        // 単発書込でも 1 オブジェクト 1 行で投入する(コスト効率は writeBatch を使うこと)
        writeBatch(tenantId, List.of(formattedLine));
    }

    @Override
    public void writeBatch(TenantId tenantId, List<String> formattedLines) {
        if (formattedLines == null || formattedLines.isEmpty()) return;
        String body = String.join("\n", formattedLines) + "\n";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String key = buildKey(tenantId);
        PutObjectRequest req =
                PutObjectRequest.builder()
                        .bucket(config.bucket())
                        .key(key)
                        .contentType(config.contentType())
                        .contentLength((long) bytes.length)
                        .build();
        s3.putObject(req, RequestBody.fromBytes(bytes));
        LOG.info(
                "S3 出力 tenant={} bucket={} key={} lines={} bytes={}",
                tenantId.value(),
                config.bucket(),
                key,
                formattedLines.size(),
                bytes.length);
    }

    private String buildKey(TenantId tenantId) {
        StringBuilder sb = new StringBuilder();
        if (!config.keyPrefix().isEmpty()) {
            sb.append(config.keyPrefix());
            if (!config.keyPrefix().endsWith("/")) sb.append('/');
        }
        sb.append(tenantId.value()).append('/');
        sb.append(LocalDate.now(ZoneOffset.UTC)).append('/');
        sb.append(System.currentTimeMillis()).append('-');
        byte[] rand = new byte[4];
        RAND.nextBytes(rand);
        sb.append(HexFormat.of().formatHex(rand));
        sb.append(".csv");
        return sb.toString();
    }
}
