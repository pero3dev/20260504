package com.example.inventory.commons.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.example.inventory.commons.tenant.TenantId;

/** OutboxPublisher の単体テスト。Kafka とテナント走査の振る舞いをモックで検証。 */
class OutboxPublisherTest {

    private TenantDirectory tenantDirectory;
    private OutboxRepository outboxRepository;
    private OutboxKafkaSender sender;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        tenantDirectory = Mockito.mock(TenantDirectory.class);
        outboxRepository = Mockito.mock(OutboxRepository.class);
        sender = Mockito.mock(OutboxKafkaSender.class);
        OutboxProperties props = new OutboxProperties(Duration.ofSeconds(1), 100, List.of("acme"));
        // テストでは TX を実体無しで擬似する: getTransaction → status を返し、commit/rollback は no-op。
        PlatformTransactionManager txManager = Mockito.mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        publisher =
                new OutboxPublisher(tenantDirectory, outboxRepository, sender, props, txManager);
    }

    @Test
    void Kafka発行成功した行のみpublishedが更新される() {
        TenantId tenant = new TenantId("acme");
        when(tenantDirectory.activeTenants()).thenReturn(List.of(tenant));

        OutboxRecord r1 = sampleRecord(1001L);
        OutboxRecord r2 = sampleRecord(1002L);
        when(outboxRepository.pickUnpublished(anyInt())).thenReturn(List.of(r1, r2));

        when(sender.send(r1)).thenReturn(CompletableFuture.completedFuture(null));
        when(sender.send(r2))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        publisher.drain();

        verify(outboxRepository).markPublished(1001L);
        verify(outboxRepository, never()).markPublished(1002L);
    }

    @Test
    void テナントが空の場合はDBにもKafkaにも触れない() {
        when(tenantDirectory.activeTenants()).thenReturn(List.of());

        publisher.drain();

        verify(outboxRepository, never()).pickUnpublished(anyInt());
        verify(sender, never()).send(any());
    }

    @Test
    void あるテナントの例外が他テナントの処理を止めない() {
        TenantId failing = new TenantId("failing-tenant");
        TenantId healthy = new TenantId("healthy-tenant");
        when(tenantDirectory.activeTenants()).thenReturn(List.of(failing, healthy));

        // 1回目(failing): 例外、2回目(healthy): 1件発行成功
        OutboxRecord rec = sampleRecord(2001L);
        when(outboxRepository.pickUnpublished(anyInt()))
                .thenThrow(new RuntimeException("DB blip"))
                .thenReturn(List.of(rec));
        when(sender.send(rec)).thenReturn(CompletableFuture.completedFuture(null));

        publisher.drain();

        verify(outboxRepository, times(2)).pickUnpublished(anyInt());
        verify(outboxRepository).markPublished(2001L);
    }

    private static OutboxRecord sampleRecord(long eventId) {
        Instant now = Instant.now();
        return new OutboxRecord(
                eventId,
                "acme",
                "inventory.movement.v1",
                "1.0",
                9999L,
                "{}",
                "trace-1",
                now,
                now,
                false);
    }
}
