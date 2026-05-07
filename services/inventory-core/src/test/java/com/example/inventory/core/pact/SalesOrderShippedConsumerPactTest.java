package com.example.inventory.core.pact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.example.inventory.core.adapter.in.kafka.SalesOrderShippedMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.LambdaDsl;
import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Interaction;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;

/**
 * Consumer-driven contract: inventory-core が wholesale から受信する {@code wholesale.order.shipped.v1}
 * メッセージの形式契約(ADR-0019 Phase 2.2)。
 *
 * <p>必須: aggregateId / code / items[].{lineNo, skuCode, locationId, quantity}。 契約外: partnerCode /
 * shippedAt(後者は inventory-core の listener が使わない、 ship 操作には影響しないため)。
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "wholesale", providerType = ProviderType.ASYNCH)
class SalesOrderShippedConsumerPactTest {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());

    @Pact(consumer = "inventory-core")
    public V4Pact salesOrderShippedV1(PactBuilder builder) {
        DslPart payload =
                LambdaDsl.newJsonBody(
                                o -> {
                                    o.numberType("aggregateId", 5001L);
                                    o.stringType("code", "SO-2026-0001");
                                    o.minArrayLike(
                                            "items",
                                            1,
                                            item -> {
                                                item.integerType("lineNo", 1);
                                                item.stringType("skuCode", "SKU-A");
                                                item.stringType("locationId", "LOC-1");
                                                item.integerType("quantity", 3);
                                            });
                                })
                        .build();

        return builder.expectsToReceiveMessageInteraction(
                        "a wholesale sales order shipped event",
                        i -> i.withContents(c -> c.withContent(payload)))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "salesOrderShippedV1", pactVersion = PactSpecVersion.V4)
    void inventoryCore_can_deserialize_sales_order_shipped_payload(
            V4Interaction.AsynchronousMessage message) throws Exception {
        byte[] body = message.getContents().getContents().getValue();
        SalesOrderShippedMessage dto =
                OBJECT_MAPPER.readValue(body, SalesOrderShippedMessage.class);

        assertThat(dto.aggregateId()).isPositive();
        assertThat(dto.code()).isNotBlank();
        assertThat(dto.items()).isNotEmpty();
        SalesOrderShippedMessage.Line line = dto.items().get(0);
        assertThat(line.lineNo()).isPositive();
        assertThat(line.skuCode()).isNotBlank();
        assertThat(line.locationId()).isNotBlank();
        assertThat(line.quantity()).isPositive();
    }
}
