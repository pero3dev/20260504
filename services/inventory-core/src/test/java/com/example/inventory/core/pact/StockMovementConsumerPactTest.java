package com.example.inventory.core.pact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.example.inventory.core.adapter.in.kafka.StockMovementMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 * Consumer-driven contract: inventory-core が tpl から受信する {@code tpl.stock.movement.v1}
 * メッセージの形式契約(ADR-0019 Phase 2.2)。
 *
 * <p>必須(StockMovementListener が実使用): code / skuCode / locationId / movementType / quantity。
 *
 * <p>契約外: aggregateId / partnerCode / referenceCode / versionAfter / occurredAt(consumer は不使用、
 * 補償もないため)。
 *
 * <p>{@code movementType} は INBOUND / OUTBOUND / ADJUSTMENT のいずれか。 ADJUSTMENT は MVP で skip 動作のため、
 * 契約上は文字列。
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "tpl", providerType = ProviderType.ASYNCH)
class StockMovementConsumerPactTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Pact(consumer = "inventory-core")
    public V4Pact stockMovementV1(PactBuilder builder) {
        DslPart payload =
                LambdaDsl.newJsonBody(
                                o -> {
                                    o.stringType("code", "MV-2026-0001");
                                    o.stringType("skuCode", "SKU-A");
                                    o.stringType("locationId", "LOC-3PL-A");
                                    o.stringMatcher(
                                            "movementType",
                                            "INBOUND|OUTBOUND|ADJUSTMENT",
                                            "INBOUND");
                                    o.integerType("quantity", 50);
                                })
                        .build();

        return builder.expectsToReceiveMessageInteraction(
                        "a tpl stock movement event",
                        i -> i.withContents(c -> c.withContent(payload)))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "stockMovementV1", pactVersion = PactSpecVersion.V4)
    void inventoryCore_can_deserialize_stock_movement_payload(
            V4Interaction.AsynchronousMessage message) throws Exception {
        byte[] body = message.getContents().getContents().getValue();
        StockMovementMessage dto = OBJECT_MAPPER.readValue(body, StockMovementMessage.class);

        assertThat(dto.code()).isNotBlank();
        assertThat(dto.skuCode()).isNotBlank();
        assertThat(dto.locationId()).isNotBlank();
        assertThat(dto.movementType()).isIn("INBOUND", "OUTBOUND", "ADJUSTMENT");
        assertThat(dto.quantity()).isPositive();
    }
}
