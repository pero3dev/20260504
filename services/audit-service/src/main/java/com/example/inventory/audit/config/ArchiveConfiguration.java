package com.example.inventory.audit.config;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.inventory.audit.adapter.out.archive.S3AuditArchiveExporter;
import com.example.inventory.audit.application.port.out.AuditArchiveExporter;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * {@code platform.audit.archive.enabled=true} のときだけ {@link S3Client} を生成する(ADR-0008)。
 *
 * <p>credential は {@link DefaultCredentialsProvider}(IRSA / 環境変数 / AWS profile)で解決。 {@code
 * endpointOverride} を指定すると LocalStack や MinIO に向けられる(path-style 強制)。
 */
@Configuration
@EnableConfigurationProperties(ArchiveProperties.class)
@ConditionalOnProperty(prefix = "platform.audit.archive", name = "enabled", havingValue = "true")
public class ArchiveConfiguration {

    @Bean
    public S3Client auditArchiveS3Client(ArchiveProperties properties) {
        software.amazon.awssdk.services.s3.S3ClientBuilder builder =
                S3Client.builder()
                        .region(Region.of(properties.region()))
                        .credentialsProvider(DefaultCredentialsProvider.create());
        if (properties.endpointOverride() != null && !properties.endpointOverride().isBlank()) {
            builder = builder.endpointOverride(URI.create(properties.endpointOverride()));
            // LocalStack / MinIO は path-style が必要(virtual-hosted-style だと bucket 名が
            // host にぶら下がるが local endpoint では DNS 解決できない)。
            builder =
                    builder.serviceConfiguration(
                            S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    @Bean
    public AuditArchiveExporter auditArchiveExporter(
            S3Client auditArchiveS3Client, ArchiveProperties properties) {
        return new S3AuditArchiveExporter(auditArchiveS3Client, properties);
    }
}
