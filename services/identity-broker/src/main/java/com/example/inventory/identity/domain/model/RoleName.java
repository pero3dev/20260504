package com.example.inventory.identity.domain.model;

import java.util.regex.Pattern;

/**
 * ロール名。{@code ROLE_*} は Spring Security の慣習で接頭辞として後付けされるため、 本フィールドは {@code INVENTORY_MANAGER}
 * のようなプレフィックス無しで保持する。
 */
public record RoleName(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    public RoleName {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("不正なロール名: " + value);
        }
    }
}
