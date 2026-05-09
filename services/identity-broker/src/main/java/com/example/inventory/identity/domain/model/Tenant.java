package com.example.inventory.identity.domain.model;

import java.time.Instant;
import java.util.regex.Pattern;

import com.example.inventory.commons.tenant.TenantId;

/**
 * テナント集約(A5、 ADR-0003 follow-up)。
 *
 * <p>identity-broker は全テナントの SoR(System of Record)として lifecycle を保持する。 ACTIVE → DEACTIVATED
 * の一方向遷移のみで、 復活は別 use case として明示的に再 register する想定(MVP では無し)。
 *
 * <p>ADR-0022 phase 5a で {@code locale}(BCP47 風 {@code ^[a-z]{2}(-[A-Z]{2})?$}、 default {@code
 * ja})を追加。 IB アクセストークン claim に乗せ web 側で {@code i18n.changeLanguage()} を駆動する。
 */
public final class Tenant {

    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{2,31}$");
    private static final Pattern LOCALE_PATTERN = Pattern.compile("^[a-z]{2}(-[A-Z]{2})?$");

    public static final String DEFAULT_LOCALE = "ja";

    private final TenantId tenantId;
    private String displayName;
    private TenantStatus status;
    private final Instant createdAt;
    private Instant deactivatedAt;
    private String locale;

    public static Tenant register(TenantId tenantId, String displayName, Instant now) {
        return register(tenantId, displayName, DEFAULT_LOCALE, now);
    }

    public static Tenant register(
            TenantId tenantId, String displayName, String locale, Instant now) {
        validate(tenantId, displayName, locale);
        return new Tenant(tenantId, displayName, TenantStatus.ACTIVE, now, null, locale);
    }

    public static Tenant restore(
            TenantId tenantId,
            String displayName,
            TenantStatus status,
            Instant createdAt,
            Instant deactivatedAt) {
        return restore(tenantId, displayName, status, createdAt, deactivatedAt, DEFAULT_LOCALE);
    }

    public static Tenant restore(
            TenantId tenantId,
            String displayName,
            TenantStatus status,
            Instant createdAt,
            Instant deactivatedAt,
            String locale) {
        validate(tenantId, displayName, locale);
        return new Tenant(tenantId, displayName, status, createdAt, deactivatedAt, locale);
    }

    private static void validate(TenantId tenantId, String displayName, String locale) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId は必須");
        if (!TENANT_ID_PATTERN.matcher(tenantId.value()).matches()) {
            throw new IllegalArgumentException(
                    "tenantId は ^[a-z0-9][a-z0-9-]{2,31}$ に従う必要がある: " + tenantId.value());
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName は必須");
        }
        if (displayName.length() > 128) {
            throw new IllegalArgumentException("displayName は 128 文字以下");
        }
        if (locale == null || !LOCALE_PATTERN.matcher(locale).matches()) {
            throw new IllegalArgumentException(
                    "locale は ^[a-z]{2}(-[A-Z]{2})?$ に従う必要がある: " + locale);
        }
    }

    private Tenant(
            TenantId tenantId,
            String displayName,
            TenantStatus status,
            Instant createdAt,
            Instant deactivatedAt,
            String locale) {
        this.tenantId = tenantId;
        this.displayName = displayName;
        this.status = status;
        this.createdAt = createdAt;
        this.deactivatedAt = deactivatedAt;
        this.locale = locale;
    }

    /** ACTIVE → DEACTIVATED に遷移。 既に DEACTIVATED なら冪等(state 維持、 戻り値で was-active を伝えない)。 */
    public void deactivate(Instant now) {
        if (status == TenantStatus.DEACTIVATED) return;
        this.status = TenantStatus.DEACTIVATED;
        this.deactivatedAt = now;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String displayName() {
        return displayName;
    }

    public TenantStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant deactivatedAt() {
        return deactivatedAt;
    }

    public String locale() {
        return locale;
    }
}
