package com.example.inventory.commons.error;

import org.springframework.http.HttpStatus;

/**
 * 機微操作({@code @RequireStepUp})に対して、現在のセッションが MFA 強度不足のまま アクセスしようとした場合に投げる例外(ADR-0007 の F1-3
 * ステップアップMFA)。
 *
 * <p>{@link com.example.inventory.commons.error.GlobalExceptionHandler} が 401 + {@code
 * WWW-Authenticate: MFA-Required} ヘッダを返却し、フロント側に MFA 再認証を要求するシグナルとなる。
 */
public class StepUpRequiredException extends BusinessException {

    public static final String CODE = "ERR_STEP_UP_REQUIRED";

    public StepUpRequiredException() {
        super("この操作にはステップアップ MFA 認証が必要です");
    }

    @Override
    public String errorCode() {
        return CODE;
    }

    @Override
    public HttpStatus statusCode() {
        return HttpStatus.UNAUTHORIZED;
    }
}
