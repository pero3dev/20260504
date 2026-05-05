package com.example.inventory.commons.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.example.inventory.commons.event.DomainEventPublisher;
import com.example.inventory.commons.tenant.TenantContext;
import com.example.inventory.commons.tenant.TenantId;

class AuditEventEmitterTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void TenantContext空のときはSYSTEMにフォールバックして発行する() {
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        AtomicReference<TenantId> seen = new AtomicReference<>();
        doAnswer(
                        inv -> {
                            seen.set(TenantContext.getOrNull());
                            return null;
                        })
                .when(publisher)
                .publish(any());

        AuditEventEmitter emitter = new AuditEventEmitter(publisher);
        emitter.emit(sample());

        // publish 中は SYSTEM がセットされている
        assertThat(seen.get()).isEqualTo(TenantId.SYSTEM);
        // emit 終了時には元の状態(=空)に戻っている
        assertThat(TenantContext.getOrNull()).isNull();
        verify(publisher).publish(any());
    }

    @Test
    void TenantContextが既に設定されているときは触らない() {
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        AtomicReference<TenantId> seen = new AtomicReference<>();
        doAnswer(
                        inv -> {
                            seen.set(TenantContext.getOrNull());
                            return null;
                        })
                .when(publisher)
                .publish(any());

        TenantId existing = new TenantId("acme");
        TenantContext.set(existing);

        AuditEventEmitter emitter = new AuditEventEmitter(publisher);
        emitter.emit(sample());

        // publish 中は元のテナント
        assertThat(seen.get()).isEqualTo(existing);
        // 既存の TenantContext は維持される
        assertThat(TenantContext.getOrNull()).isEqualTo(existing);
    }

    @Test
    void publish例外でもfinallyでTenantContextを片付ける() {
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        doAnswer(
                        inv -> {
                            throw new RuntimeException("kafka unreachable");
                        })
                .when(publisher)
                .publish(any());

        AuditEventEmitter emitter = new AuditEventEmitter(publisher);

        // フォールバックパス: 例外が伝搬しても Context が SYSTEM のまま残らないこと
        try {
            emitter.emit(sample());
        } catch (RuntimeException ignored) {
            // emit は @Transactional REQUIRES_NEW で例外を再送出する
        }
        assertThat(TenantContext.getOrNull()).isNull();
    }

    private static AuditEvent sample() {
        return new AuditEvent(
                "USER_AUTHENTICATE",
                "User",
                "alice@example.com",
                "anonymous",
                "unknown",
                AuditOutcome.SUCCESS,
                null,
                false,
                "{}",
                Instant.now());
    }
}
