package com.example.inventory.commons.event;

import java.util.List;

import com.example.inventory.commons.tenant.TenantId;

/**
 * 設定値({@code platform.outbox.tenants})からテナント一覧を返す素朴な実装。
 *
 * <p>本番では Identity & Tenant サービスへの問い合わせ実装に差し替える前提。 インターフェースを変更せずに置換可能なよう、利用側はポート({@link
 * TenantDirectory})にのみ依存する。
 */
public class StaticTenantDirectory implements TenantDirectory {

    private final List<TenantId> tenants;

    public StaticTenantDirectory(OutboxProperties properties) {
        this.tenants = properties.tenants().stream().map(TenantId::new).toList();
    }

    @Override
    public List<TenantId> activeTenants() {
        return tenants;
    }
}
