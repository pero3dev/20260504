package com.example.inventory.commons.audit;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.event.DomainEventPublisher;
import com.example.inventory.commons.tenant.TenantContext;
import com.example.inventory.commons.tenant.TenantId;

/**
 * 監査イベントを別トランザクションで発行するためのラッパ。
 *
 * <p><b>なぜ REQUIRES_NEW か:</b> 業務ロジック側で {@code BusinessException} が発生すると
 * 呼び出し元のトランザクションがロールバックされる。同じトランザクションで監査も書くと、 監査レコードまでロールバックされてしまう。J-SOX 観点では「失敗した操作の記録」も
 * 重要なため、監査は別トランザクションで commit する必要がある。
 *
 * <p><b>なぜ TenantContext のシステムフォールバックを行うか:</b> Identity Broker のログイン等、 JWT 確立前(=TenantContext
 * 空)のリクエストでも監査を残したい。本クラスが emit 直前に {@link TenantId#SYSTEM} ({@code "platform"}) をセットして publisher が
 * {@code TenantContext.required()} を要求するパスを満たす。emit 後に必ず元の状態(空)へ戻す。
 *
 * <p>注意: SYSTEM フォールバックを使った emit は、Outbox 行の {@code tenant_id="platform"} になる。 Bridge
 * 方式のサービス(=tenant_<id> スキーマ毎の outbox)では tenant_platform スキーマが無いと MyBatis インターセプタが失敗する。SYSTEM
 * フォールバックは Pool 方式サービス(Identity/Notification 等) のテナントレスエンドポイントでのみ使う想定。
 */
public class AuditEventEmitter {

    private final DomainEventPublisher publisher;

    public AuditEventEmitter(DomainEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void emit(AuditEvent event) {
        TenantId existing = TenantContext.getOrNull();
        boolean tenantWasEmpty = (existing == null);
        if (tenantWasEmpty) {
            TenantContext.set(TenantId.SYSTEM);
        }
        try {
            publisher.publish(event);
        } finally {
            if (tenantWasEmpty) {
                TenantContext.clear();
            }
        }
    }
}
