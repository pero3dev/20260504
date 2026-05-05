package com.example.inventory.commons.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.example.inventory.commons.error.PlatformProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 認証エラー(401)を RFC 7807 ProblemDetail として返す共通ハンドラ。
 *
 * <p>フィルタチェーン段階で発生する例外は {@code @ControllerAdvice} で捕捉できないため、 Security の {@link
 * AuthenticationEntryPoint} 経由で同じ形式に揃える。
 */
public final class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ProblemAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException, ServletException {
        ProblemDetail pd =
                PlatformProblemDetails.of(
                        HttpStatus.UNAUTHORIZED,
                        "ERR_UNAUTHENTICATED",
                        "認証が必要です",
                        traceId(request));
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), pd);
    }

    private static String traceId(HttpServletRequest request) {
        // 認証エラーは認証フィルタチェーン段階で発生するため、MDC.traceId はまだセットされていない。
        // OTel/Datadog エージェントが付与するトレースIDヘッダから取得する。
        return request.getHeader("X-B3-TraceId");
    }
}
