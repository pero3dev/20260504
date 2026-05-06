package com.example.inventory.workflow.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

public class WorkflowDefinitionNotFoundException extends BusinessException {

    public static final String CODE = "ERR_WORKFLOW_DEFINITION_NOT_FOUND";

    public WorkflowDefinitionNotFoundException(String key) {
        super("Workflow 定義が見つかりません: " + key);
    }

    @Override
    public String errorCode() {
        return CODE;
    }

    @Override
    public HttpStatus statusCode() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
