package com.example.inventory.commons.tenant;

import java.util.regex.Pattern;

/** 型付きテナントID。形式は小文字英数 + ハイフン、3〜32文字。 Bridge方式におけるスキーマ名は {@code "tenant_" + value} として導出される。 */
public record TenantId(String value) {

    private static final Pattern VALID = Pattern.compile("^[a-z0-9][a-z0-9-]{2,31}$");

    /**
     * テナント不在のシステムコンテキスト用テナントID。
     *
     * <p>用途: Identity Broker のログインなど、JWT 確立前のリクエストで監査イベントを発行する際の outbox メタデータとして使う(commons-audit の
     * AuditEventEmitter が自動フォールバック)。 業務データ用ではない。Bridge 方式の業務サービスは tenant_platform スキーマを持たないため、 Pool
     * 方式サービス(Identity / Notification 等)からの emit のみ想定する。
     */
    public static final TenantId SYSTEM = new TenantId("platform");

    public TenantId {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("不正なテナントID: " + value);
        }
    }

    public String schemaName() {
        return "tenant_" + value;
    }

    /** {@link #SYSTEM} との比較用。 */
    public boolean isSystem() {
        return SYSTEM.value().equals(value);
    }
}
