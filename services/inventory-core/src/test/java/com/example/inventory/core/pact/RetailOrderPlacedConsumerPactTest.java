package com.example.inventory.core.pact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.example.inventory.core.adapter.in.kafka.OrderPlacedMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Interaction;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;

/**
 * Consumer-driven contract: inventory-core が retail-ec から受信する {@code retail.order.placed.v1}
 * メッセージの形式契約(ADR-0019 Phase 2.2)。
 *
 * <p>必須フィールド(Consumer の OrderPlacedListener が実使用): aggregateId / code / items[].{lineNo, skuCode,
 * locationId, quantity}。
 *
 * <p>契約外: customerEmail / currency / totalAmount / unitPrice。 Provider が送出しても Consumer は使わない。
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "retail-ec", providerType = ProviderType.ASYNCH)
class RetailOrderPlacedConsumerPactTest {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());

    @Pact(consumer = "inventory-core")
    public V4Pact retailOrderPlacedV1(PactBuilder builder) {
        PactDslJsonBody itemTemplate =
                new PactDslJsonBody()
                        .integerType("lineNo", 1)
                        .stringType("skuCode", "SKU-A")
                        .stringType("locationId", "LOC-1")
                        .integerType("quantity", 2);

        PactDslJsonBody payload =
                new PactDslJsonBody()
                        .numberType("aggregateId", 6001L)
                        .stringType("code", "ORD-2026-0001")
                        .minArrayLike("items", 1, itemTemplate)
                        .stringMatcher(
                                "occurredAt",
                                "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z",
                                "2026-05-06T10:00:00Z");

        return builder.expectsToReceive("a retail order placed event", "core/interaction/message")
                .with(Map.of("message.contents", payload))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "retailOrderPlacedV1", pactVersion = PactSpecVersion.V4)
    void inventoryCore_can_deserialize_retail_order_placed_payload(
            V4Interaction.AsynchronousMessage message) throws Exception {
        byte[] body = message.getContents().getContents().getValue();
        OrderPlacedMessage dto = OBJECT_MAPPER.readValue(body, OrderPlacedMessage.class);

        assertThat(dto.aggregateId()).isPositive();
        assertThat(dto.code()).isNotBlank();
        assertThat(dto.items()).isNotEmpty();
        OrderPlacedMessage.Line line = dto.items().get(0);
        assertThat(line.lineNo()).isPositive();
        assertThat(line.skuCode()).isNotBlank();
        assertThat(line.locationId()).isNotBlank();
        assertThat(line.quantity()).isPositive();
        assertThat(dto.occurredAt()).isNotNull();
    }
}
