package com.example.inventory.workflow.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

public class WorkflowNotFoundException extends BusinessException {

    public static final String CODE = "ERR_WORKFLOW_NOT_FOUND";

    public WorkflowNotFoundException(long workflowId) {
        super("ワークフローインスタンスが見つかりません: " + workflowId);
    }

    @Override
    public String errorCode() {
        return CODE;
    }

    @Override
    public HttpStatus statusCode() {
        return HttpStatus.NOT_FOUND;
    }
}
