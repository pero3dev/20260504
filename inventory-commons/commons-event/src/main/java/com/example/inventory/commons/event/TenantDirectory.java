package com.example.inventory.commons.event;

import java.util.List;

import com.example.inventory.commons.tenant.TenantId;

/**
 * 各サービスの OutboxPublisher が、自身の outbox を走査する対象テナントを取得するポート。
 *
 * <p>初期実装は設定値ベース({@code platform.outbox.tenants}) で十分。 将来的には Identity & Tenant
 * サービスへのキャッシュ付き問い合わせに置き換える。
 *
 * <p>テナントの増減時に publisher が即時追従する必要は無い(数分の遅延は許容)。
 */
public interface TenantDirectory {

    /** outbox 走査対象のテナント一覧。並び順は問わない。 */
    List<TenantId> activeTenants();
}
