package com.example.inventory.core.adapter.in.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.example.inventory.commons.tenant.TenantContext;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.core.application.port.in.RegisterSkuFromMasterUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code master.product.v1} を購読し、Inventory Core の SKU 投影テーブルへ反映する。
 *
 * <p>Master Data はマルチテナントで Bridge 方式のため、{@code tenant_id} ヘッダから {@link TenantContext}
 * を設定し、その上でユースケースを呼ぶ。これによりテーブル書込先が テナントスキーマに切替わる。
 *
 * <p>at-least-once。冪等性は SkuRegistryPort 側の version 比較 upsert で担保する。
 */
@Component
public class SkuMasterListener {

    private static final Logger LOG = LoggerFactory.getLogger(SkuMasterListener.class);
    private static final String HEADER_TENANT_ID = "tenant_id";

    private final RegisterSkuFromMasterUseCase useCase;
    private final ObjectMapper objectMapper;

    public SkuMasterListener(RegisterSkuFromMasterUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "master.product.v1",
            groupId = "${spring.application.name}-sku-projection")
    public void onMasterEvent(ConsumerRecord<String, String> record, Acknowledgment ack)
            throws Exception {
        String tenantIdValue = headerValue(record, HEADER_TENANT_ID);
        if (tenantIdValue == null) {
            LOG.warn(
                    "tenant_id 無しの master.product.v1 をスキップ topic={} partition={} offset={}",
                    record.topic(),
                    record.partition(),
                    record.offset());
            ack.acknowledge();
            return;
        }
        try {
            TenantContext.set(new TenantId(tenantIdValue));
            SkuMasterMessage msg = objectMapper.readValue(record.value(), SkuMasterMessage.class);
            useCase.register(
                    new RegisterSkuFromMasterUseCase.Command(
                            msg.aggregateId(),
                            msg.code(),
                            msg.name(),
                            msg.unitOfMeasure(),
                            msg.versionAfter(),
                            msg.occurredAt()));
            ack.acknowledge();
            LOG.debug(
                    "SKU 投影更新 tenant={} code={} version={}",
                    tenantIdValue,
                    msg.code(),
                    msg.versionAfter());
        } finally {
            TenantContext.clear();
        }
    }

    private static String headerValue(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
