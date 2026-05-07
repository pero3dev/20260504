package com.example.inventory.notification.config;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.inventory.notification.adapter.out.email.LoggingEmailSender;
import com.example.inventory.notification.adapter.out.email.SesEmailSender;
import com.example.inventory.notification.application.port.out.EmailSender;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * メール送信器の Bean 切替(A2、 ADR-0007 follow-up)。
 *
 * <p>{@code notification.email.provider}:
 *
 * <ul>
 *   <li>{@code logging}(default、 default 値で他の Bean が無いとき) — {@link LoggingEmailSender}
 *   <li>{@code ses} — {@link SesEmailSender}(AWS SES)
 * </ul>
 *
 * <p>新 provider(SMTP / SendGrid / Slack 等)を加える時は {@code @ConditionalOnProperty} を増やすだけで他の Bean
 * を妨げない。
 */
@Configuration
@EnableConfigurationProperties(EmailSenderProperties.class)
public class EmailSenderConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "notification.email", name = "provider", havingValue = "ses")
    public SesClient sesClient(EmailSenderProperties properties) {
        software.amazon.awssdk.services.ses.SesClientBuilder builder =
                SesClient.builder()
                        .region(Region.of(properties.region()))
                        .credentialsProvider(DefaultCredentialsProvider.create());
        if (properties.endpointOverride() != null && !properties.endpointOverride().isBlank()) {
            builder = builder.endpointOverride(URI.create(properties.endpointOverride()));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "notification.email", name = "provider", havingValue = "ses")
    public EmailSender sesEmailSender(SesClient sesClient, EmailSenderProperties properties) {
        return new SesEmailSender(sesClient, properties.from());
    }

    /**
     * default(provider 未指定 or {@code logging})は LoggingEmailSender。
     * {@code @ConditionalOnMissingBean} で SES 等の他 Bean が居ればこちらは生成されない。
     */
    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender loggingEmailSender() {
        return new LoggingEmailSender();
    }
}
