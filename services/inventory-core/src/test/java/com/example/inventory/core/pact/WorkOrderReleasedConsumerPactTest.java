package com.example.inventory.core.pact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.example.inventory.core.adapter.in.kafka.WorkOrderReleasedMessage;
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
 * Consumer-driven contract: inventory-core が manufacturing から受信する {@code
 * manufacturing.work_order.released.v1} メッセージの形式契約(ADR-0019 Phase 2.2)。
 *
 * <p>必須: aggregateId / code / locationId / components[].{componentSkuCode, requiredQuantity}。 これらは
 * ConsumeWorkOrderComponentsService が部品を reserve+ship するために実際に使うフィールド。
 *
 * <p>契約外: productSkuCode / plannedQuantity / plannedStartDate(consumer は components[] さえあれば消費可)。
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "manufacturing", providerType = ProviderType.ASYNCH)
class WorkOrderReleasedConsumerPactTest {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());

    @Pact(consumer = "inventory-core")
    public V4Pact workOrderReleasedV1(PactBuilder builder) {
        DslPart payload =
                LambdaDsl.newJsonBody(
                                o -> {
                                    o.numberType("aggregateId", 7001L);
                                    o.stringType("code", "WO-2026-0001");
                                    o.stringType("locationId", "LOC-FACTORY-A");
                                    o.minArrayLike(
                                            "components",
                                            1,
                                            comp -> {
                                                comp.stringType("componentSkuCode", "SKU-A");
                                                comp.integerType("requiredQuantity", 20);
                                            });
                                })
                        .build();

        return builder.expectsToReceiveMessageInteraction(
                        "a manufacturing work order released event",
                        i -> i.withContents(c -> c.withContent(payload)))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "workOrderReleasedV1", pactVersion = PactSpecVersion.V4)
    void inventoryCore_can_deserialize_work_order_released_payload(
            V4Interaction.AsynchronousMessage message) throws Exception {
        byte[] body = message.getContents().getContents().getValue();
        WorkOrderReleasedMessage dto =
                OBJECT_MAPPER.readValue(body, WorkOrderReleasedMessage.class);

        assertThat(dto.aggregateId()).isPositive();
        assertThat(dto.code()).isNotBlank();
        assertThat(dto.locationId()).isNotBlank();
        assertThat(dto.components()).isNotEmpty();
        WorkOrderReleasedMessage.Component c = dto.components().get(0);
        assertThat(c.componentSkuCode()).isNotBlank();
        assertThat(c.requiredQuantity()).isPositive();
    }
}
