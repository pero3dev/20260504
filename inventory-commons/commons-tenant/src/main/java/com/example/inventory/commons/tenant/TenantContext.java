package com.example.inventory.commons.tenant;

/**
 * リクエストごとのテナントコンテキスト。
 *
 * <p>{@link TenantContextFilter} が検証済みJWTから設定し、Bridge方式のマルチテナンシ (ADR-0003) のために {@code search_path}
 * を切り替える MyBatis インターセプタや、 テナント単位のロジックを持つサービスから参照される。
 *
 * <p>{@link ThreadLocal} を採用しているのは、当面の非同期境界が {@code TaskDecorator} / {@code
 * TenantPropagatingExecutor} 経由で 明示的に伝播される構成に限定されているため。
 */
public final class TenantContext {

    private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        CURRENT.set(tenantId);
    }

    public static TenantId required() {
        TenantId t = CURRENT.get();
        if (t == null) {
            throw new IllegalStateException("テナントコンテキストが未設定。DBアクセスは TenantContext 配下で実行する必要があります。");
        }
        return t;
    }

    /** 現在のテナントを返す(未設定なら null)。テナントレス処理での分岐判定に使う。 */
    public static TenantId getOrNull() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
