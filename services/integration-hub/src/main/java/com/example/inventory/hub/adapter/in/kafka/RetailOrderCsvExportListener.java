package com.example.inventory.hub.adapter.in.kafka;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.hub.adapter.out.file.LocalFileDestination;
import com.example.inventory.hub.application.port.out.OutboundDestination;
import com.example.inventory.hub.config.HubProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code retail.order.placed.v1} を購読し、CSV 化して {@link OutboundDestination} に追記するアダプタ。
 *
 * <p>{@code platform.hub.adapters.retail-order-csv.enabled=true} の時のみ Bean 化される。
 *
 * <p>失敗時は {@link UncheckedIOException} を伝搬 → ack されず Spring Kafka の retry → DLQ に流れる。 MVP
 * は補償なし(冪等性は外部側で吸収する想定、二重出力は次のステップで dedupe)。
 */
@Component
@ConditionalOnProperty(
        prefix = "platform.hub.adapters.retail-order-csv",
        name = "enabled",
        havingValue = "true")
public class RetailOrderCsvExportListener {

    private static final Logger LOG = LoggerFactory.getLogger(RetailOrderCsvExportListener.class);
    private static final String ADAPTER_NAME = "retail-order-csv";
    private static final String HEADER_TENANT_ID = "tenant_id";

    private final OutboundDestination destination;
    private final ObjectMapper objectMapper;
    private final RetailOrderCsvFormatter formatter;

    public RetailOrderCsvExportListener(HubProperties properties, ObjectMapper objectMapper) {
        HubProperties.AdapterConfig cfg = properties.adapter(ADAPTER_NAME);
        if (cfg.baseDir() == null || cfg.fileName() == null) {
            throw new IllegalStateException(
                    "platform.hub.adapters.retail-order-csv.{baseDir,fileName} の設定が必要");
        }
        this.destination = new LocalFileDestination(Path.of(cfg.baseDir()), cfg.fileName());
        this.objectMapper = objectMapper;
        this.formatter = new RetailOrderCsvFormatter();
    }

    @KafkaListener(
            topics = "retail.order.placed.v1",
            groupId = "${spring.application.name}-retail-order-csv")
    public void onRetailOrderPlaced(ConsumerRecord<String, String> record, Acknowledgment ack)
            throws IOException {
        String tenantIdValue = headerValue(record, HEADER_TENANT_ID);
        if (tenantIdValue == null) {
            LOG.warn(
                    "tenant_id 無しの retail.order.placed.v1 をスキップ partition={} offset={}",
                    record.partition(),
                    record.offset());
            ack.acknowledge();
            return;
        }
        RetailOrderPlacedMessage msg =
                objectMapper.readValue(record.value(), RetailOrderPlacedMessage.class);

        List<String> rows = formatter.format(msg);
        TenantId tenantId = new TenantId(tenantIdValue);
        for (String row : rows) {
            destination.write(tenantId, row);
        }
        ack.acknowledge();
        LOG.debug(
                "retail-order-csv 出力 tenant={} order={} lines={}",
                tenantIdValue,
                msg.code(),
                rows.size());
    }

    private static String headerValue(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
