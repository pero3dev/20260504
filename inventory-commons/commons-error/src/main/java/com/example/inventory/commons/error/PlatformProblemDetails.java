package com.example.inventory.commons.error;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * RFC 7807 ProblemDetail を、本プラットフォーム標準の拡張フィールド付きで生成する。
 *
 * <p>標準拡張:
 *
 * <ul>
 *   <li>{@code errorCode} — フロントエンドが i18n に利用する安定コード。
 *   <li>{@code traceId} — 現在の OpenTelemetry トレースID。サポート連携用。
 * </ul>
 */
public final class PlatformProblemDetails {

    private static final String TYPE_BASE = "https://errors.example.com/";

    private PlatformProblemDetails() {}

    public static ProblemDetail of(
            HttpStatus status, String errorCode, String detail, String traceId) {
        return of(status, errorCode, detail, traceId, Map.of());
    }

    public static ProblemDetail of(
            HttpStatus status,
            String errorCode,
            String detail,
            String traceId,
            Map<String, Object> extraProperties) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(TYPE_BASE + errorCode));
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("errorCode", errorCode);
        if (traceId != null) {
            pd.setProperty("traceId", traceId);
        }
        extraProperties.forEach(pd::setProperty);
        return pd;
    }
}
