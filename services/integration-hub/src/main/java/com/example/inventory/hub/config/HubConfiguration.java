package com.example.inventory.hub.config;

import java.net.URI;
import java.nio.file.Path;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.example.inventory.commons.security.PlatformSecurity;
import com.example.inventory.hub.adapter.out.file.LocalFileDestination;
import com.example.inventory.hub.adapter.out.s3.S3Destination;
import com.example.inventory.hub.application.port.out.OutboundDestination;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Hub の Spring 設定 + {@link OutboundDestination} factory(A3)。
 *
 * <p>各 listener が adapter 名を渡して {@link #destinationFor} を呼び、 {@code
 * platform.hub.adapters.<name>.type} に応じて Local / S3 を切替えた destination を取得する。
 */
@Configuration
@EnableConfigurationProperties(HubProperties.class)
public class HubConfiguration {

    private static final String[] PERMIT_ALL = {"/actuator/health/**", "/actuator/info", "/error"};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, PlatformSecurity platform)
            throws Exception {
        return platform.applyDefaults(http)
                .authorizeHttpRequests(
                        reg ->
                                reg.requestMatchers(PERMIT_ALL)
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .build();
    }

    /**
     * adapter 名から destination を構築する factory。 listener 側で 1 度呼んでフィールド保持する想定 (call-per-message ではない)。
     */
    @Bean
    public DestinationFactory destinationFactory(HubProperties properties) {
        return new DestinationFactory(properties, HubConfiguration::s3ClientFor);
    }

    /**
     * {@code S3Destination} 生成時に呼ばれる S3Client builder。 SDK 側 endpoint override が指定されていれば LocalStack
     * / MinIO 用に path-style を強制。
     */
    public static S3Client s3ClientFor(HubProperties.S3Config config) {
        software.amazon.awssdk.services.s3.S3ClientBuilder builder =
                S3Client.builder()
                        .region(Region.of(config.region()))
                        .credentialsProvider(DefaultCredentialsProvider.create());
        if (config.endpointOverride() != null && !config.endpointOverride().isBlank()) {
            builder = builder.endpointOverride(URI.create(config.endpointOverride()));
            builder =
                    builder.serviceConfiguration(
                            S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    /** adapter 名 → {@link OutboundDestination} を提供する factory。 listener コード側を type 切替不要にする。 */
    public static class DestinationFactory {

        private final HubProperties properties;
        private final java.util.function.Function<HubProperties.S3Config, S3Client> s3Builder;

        public DestinationFactory(
                HubProperties properties,
                java.util.function.Function<HubProperties.S3Config, S3Client> s3Builder) {
            this.properties = properties;
            this.s3Builder = s3Builder;
        }

        public OutboundDestination create(String adapterName) {
            HubProperties.AdapterConfig cfg = properties.adapter(adapterName);
            return switch (cfg.type()) {
                case "local" -> {
                    if (cfg.baseDir() == null || cfg.fileName() == null) {
                        throw new IllegalStateException(
                                "platform.hub.adapters."
                                        + adapterName
                                        + ".{baseDir,fileName} の設定が必要");
                    }
                    yield new LocalFileDestination(Path.of(cfg.baseDir()), cfg.fileName());
                }
                case "s3" -> {
                    if (cfg.s3() == null) {
                        throw new IllegalStateException(
                                "platform.hub.adapters." + adapterName + ".s3 の設定が必要");
                    }
                    yield new S3Destination(s3Builder.apply(cfg.s3()), cfg.s3());
                }
                default ->
                        throw new IllegalStateException(
                                "未知の adapter.type: " + cfg.type() + "(local|s3 のいずれか)");
            };
        }
    }
}
