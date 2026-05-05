package com.example.inventory.commons.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * すべての REST コントローラ例外を RFC 7807 ProblemDetail に変換する横断ハンドラ (ADR-0006)。
 *
 * <p>マッピング:
 *
 * <ul>
 *   <li>{@link BusinessException} → {@link BusinessException#statusCode()} + errorCode + message
 *   <li>{@link StepUpRequiredException}(BusinessException 派生) → 401 + {@code WWW-Authenticate} ヘッダ
 *   <li>{@link MethodArgumentNotValidException} / {@link ConstraintViolationException} / {@link
 *       HttpMessageNotReadableException} / {@link IllegalArgumentException} → 400
 *   <li>その他 {@link Exception} → 500(内部詳細はログに残しレスポンスには出さない)
 * </ul>
 *
 * <p>order を {@link Ordered#HIGHEST_PRECEDENCE} 寄りに指定し、サービス側の 個別 advice より優先する(サービス側 advice
 * は本ハンドラを知った上で限定的に上書きする想定)。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(StepUpRequiredException.class)
    public ResponseEntity<ProblemDetail> handleStepUp(StepUpRequiredException e) {
        ProblemDetail pd =
                PlatformProblemDetails.of(
                        HttpStatus.UNAUTHORIZED, e.errorCode(), e.getMessage(), traceId());
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.WWW_AUTHENTICATE, "MFA-Required realm=\"step-up\"");
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(pd, headers, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException e) {
        HttpStatus status = e.statusCode();
        ProblemDetail pd =
                PlatformProblemDetails.of(status, e.errorCode(), e.getMessage(), traceId());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBeanValidation(MethodArgumentNotValidException e) {
        Map<String, Object> extras = new LinkedHashMap<>();
        List<Map<String, String>> fields =
                e.getBindingResult().getFieldErrors().stream()
                        .map(
                                f ->
                                        Map.of(
                                                "field", f.getField(),
                                                "rejected", String.valueOf(f.getRejectedValue()),
                                                "message", String.valueOf(f.getDefaultMessage())))
                        .toList();
        extras.put("validationErrors", fields);
        ProblemDetail pd =
                PlatformProblemDetails.of(
                        HttpStatus.BAD_REQUEST,
                        "ERR_VALIDATION",
                        "リクエストの検証に失敗しました",
                        traceId(),
                        extras);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException e) {
        Map<String, Object> extras = new LinkedHashMap<>();
        List<Map<String, String>> fields =
                e.getConstraintViolations().stream()
                        .map(
                                v ->
                                        Map.of(
                                                "path",
                                                        String.valueOf(
                                                                ((ConstraintViolation<?>) v)
                                                                        .getPropertyPath()),
                                                "rejected", String.valueOf(v.getInvalidValue()),
                                                "message", v.getMessage()))
                        .toList();
        extras.put("validationErrors", fields);
        ProblemDetail pd =
                PlatformProblemDetails.of(
                        HttpStatus.BAD_REQUEST,
                        "ERR_VALIDATION",
                        "パラメータの検証に失敗しました",
                        traceId(),
                        extras);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException e) {
        ProblemDetail pd =
                PlatformProblemDetails.of(
                        HttpStatus.BAD_REQUEST,
                        "ERR_MALFORMED_REQUEST",
                        "リクエストボディを解釈できませんでした",
                        traceId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException e) {
        // 業務由来の値域違反は BusinessException に昇格すべき。ここに到達するのは
        // 想定外の引数(URL のパス変数キャストミス等)。クライアント不正として 400 を返す。
        ProblemDetail pd =
                PlatformProblemDetails.of(
                        HttpStatus.BAD_REQUEST, "ERR_BAD_ARGUMENT", e.getMessage(), traceId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    /** 本ハンドラの最終捕捉。詳細はログのみ、レスポンスには出さない(情報漏洩防止)。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        LOG.error("想定外の例外がコントローラ層に到達: {}", e.toString(), e);
        ProblemDetail pd =
                PlatformProblemDetails.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "ERR_INTERNAL",
                        "サーバ内部エラーが発生しました",
                        traceId());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    private static String traceId() {
        return MDC.get("traceId");
    }
}
