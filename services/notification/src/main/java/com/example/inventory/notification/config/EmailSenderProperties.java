package com.example.inventory.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * メール送信器の設定。
 *
 * <p>{@code notification.email.provider} は {@code logging}(default、 dev / loadtest / unit test)/
 * {@code ses}(本番)を切替える。 SES の場合は {@code from} と {@code region} を指定。
 *
 * <p>{@code endpointOverride} は LocalStack 用(本番では空)。
 */
@ConfigurationProperties(prefix = "notification.email")
public record EmailSenderProperties(
        String provider, String from, String region, String endpointOverride) {

    public EmailSenderProperties {
        if (provider == null || provider.isBlank()) provider = "logging";
        if (region == null || region.isBlank()) region = "ap-northeast-1";
        if ("ses".equalsIgnoreCase(provider) && (from == null || from.isBlank())) {
            throw new IllegalArgumentException(
                    "notification.email.provider=ses の場合は notification.email.from は必須");
        }
    }
}
