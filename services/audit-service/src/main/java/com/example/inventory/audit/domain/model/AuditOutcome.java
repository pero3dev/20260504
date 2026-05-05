package com.example.inventory.audit.domain.model;

/** 元イベントの結末。commons-audit の同名 enum と意味的に同じ(値は文字列で受信)。 */
public enum AuditOutcome {
    SUCCESS,
    BUSINESS_FAILURE,
    SYSTEM_FAILURE
}
