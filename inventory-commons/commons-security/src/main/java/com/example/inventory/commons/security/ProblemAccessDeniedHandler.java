package com.example.inventory.commons.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.example.inventory.commons.error.PlatformProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 認可エラー(403)を RFC 7807 ProblemDetail として返す共通ハンドラ。 */
public final class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        ProblemDetail pd =
                PlatformProblemDetails.of(
                        HttpStatus.FORBIDDEN, "ERR_FORBIDDEN", "この操作を行う権限がありません", traceId(request));
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), pd);
    }

    private static String traceId(HttpServletRequest request) {
        return request.getHeader("X-B3-TraceId");
    }
}
