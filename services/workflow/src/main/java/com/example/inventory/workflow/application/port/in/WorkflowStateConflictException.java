package com.example.inventory.workflow.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/**
 * Workflow の状態遷移が現状態から不可能な場合に投げる。
 *
 * <p>HTTP 409。例: 既に COMPLETED のインスタンスへの completeStep / failStep / cancel。
 */
public class WorkflowStateConflictException extends BusinessException {

    public static final String CODE = "ERR_WORKFLOW_STATE_CONFLICT";

    public WorkflowStateConflictException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return CODE;
    }

    @Override
    public HttpStatus statusCode() {
        return HttpStatus.CONFLICT;
    }
}
