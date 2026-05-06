package com.example.inventory.core.adapter.in.kafka;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.example.inventory.commons.error.BusinessException;
import com.example.inventory.commons.tenant.TenantContext;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.core.application.port.in.EmitWholesaleReservationFailedUseCase;
import com.example.inventory.core.application.port.in.InventoryNotFoundForOrderException;
import com.example.inventory.core.application.port.in.ReserveOrderUseCase;
import com.example.inventory.core.application.port.in.UnknownSkuException;
import com.example.inventory.core.domain.model.InsufficientStockException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code wholesale.order.placed.v1} を購読する Kafka リスナ。
 *
 * <p>Wholesale から飛んできた受注確定イベントを受けて、{@link ReserveOrderUseCase} に渡し各 Inventory を引当てる。 Retail/EC の
 * {@code OrderPlacedListener} と完全並列。groupId / 補償発行先トピックのみ業態別に分離。
 *
 * <p>失敗時の挙動(Saga Phase 2):
 *
 * <ul>
 *   <li>{@link InsufficientStockException}(在庫不足、業務ルール違反): 補償イベントを別 TX で発行 → ack
 *   <li>{@link UnknownSkuException} / {@link InventoryNotFoundForOrderException} (投影遅延の可能性):
 *       同様に補償イベントを発行 → ack
 *   <li>その他想定外の例外: ack せず Spring Kafka 既定 retry → DLQ
 * </ul>
 */
@Component
public class WholesaleOrderPlacedListener {

    private static final Logger LOG = LoggerFactory.getLogger(WholesaleOrderPlacedListener.class);
    private static final String HEADER_TENANT_ID = "tenant_id";
    private static final String HEADER_EVENT_ID = "event_id";

    private final ReserveOrderUseCase reserveUseCase;
    private final EmitWholesaleReservationFailedUseCase failureEmitter;
    private final ObjectMapper objectMapper;

    public WholesaleOrderPlacedListener(
            ReserveOrderUseCase reserveUseCase,
            EmitWholesaleReservationFailedUseCase failureEmitter,
            ObjectMapper objectMapper) {
        this.reserveUseCase = reserveUseCase;
        this.failureEmitter = failureEmitter;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "wholesale.order.placed.v1",
            groupId = "${spring.application.name}-wholesale-order-reserve")
    public void onWholesaleOrderPlaced(ConsumerRecord<String, String> record, Acknowledgment ack)
            throws Exception {
        String tenantIdValue = headerValue(record, HEADER_TENANT_ID);
        if (tenantIdValue == null) {
            LOG.warn(
                    "tenant_id 無しの wholesale.order.placed.v1 をスキップ partition={} offset={}",
                    record.partition(),
                    record.offset());
            ack.acknowledge();
            return;
        }
        long eventId = parseEventId(record);

        WholesaleOrderPlacedMessage msg =
                objectMapper.readValue(record.value(), WholesaleOrderPlacedMessage.class);

        try {
            TenantContext.set(new TenantId(tenantIdValue));
            reserveUseCase.reserveForOrder(
                    new ReserveOrderUseCase.Command(
                            eventId,
                            msg.aggregateId(),
                            msg.code(),
                            msg.items().stream()
                                    .map(
                                            l ->
                                                    new ReserveOrderUseCase.Command.Line(
                                                            l.lineNo(),
                                                            l.skuCode(),
                                                            l.locationId(),
                                                            l.quantity()))
                                    .collect(Collectors.toList())));
            ack.acknowledge();
            LOG.debug(
                    "Wholesale SalesOrder {} の引当を完了({}行) tenant={}",
                    msg.code(),
                    msg.items().size(),
                    tenantIdValue);
        } catch (BusinessException e) {
            String failedSku = firstSkuOrEmpty(msg);
            String failedLoc = firstLocationOrEmpty(msg);
            LOG.warn(
                    "Wholesale SalesOrder {} の引当が業務理由で失敗、補償発行: errorCode={} reason={}",
                    msg.code(),
                    e.errorCode(),
                    e.getMessage());
            failureEmitter.emit(
                    new EmitWholesaleReservationFailedUseCase.Command(
                            msg.aggregateId(),
                            msg.code(),
                            e.errorCode(),
                            e.getMessage(),
                            failedSku,
                            failedLoc));
            ack.acknowledge();
        } finally {
            TenantContext.clear();
        }
    }

    private static String firstSkuOrEmpty(WholesaleOrderPlacedMessage msg) {
        return msg.items().isEmpty() ? "" : msg.items().get(0).skuCode();
    }

    private static String firstLocationOrEmpty(WholesaleOrderPlacedMessage msg) {
        return msg.items().isEmpty() ? "" : msg.items().get(0).locationId();
    }

    private static long parseEventId(ConsumerRecord<?, ?> record) {
        String raw = headerValue(record, HEADER_EVENT_ID);
        if (raw == null) return 0L;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String headerValue(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
