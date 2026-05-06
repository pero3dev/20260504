package com.example.inventory.core.pact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.example.inventory.core.adapter.in.kafka.WholesaleOrderPlacedMessage;
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
 * Consumer-driven contract test: inventory-core が Wholesale から受信する {@code
 * wholesale.order.placed.v1} メッセージの形式契約。
 *
 * <p>本テストの位置付け(ADR-0014 の Future complement):
 *
 * <ul>
 *   <li>inventory-core 側で「期待する Kafka メッセージの形式」を Pact ファイルとして出力(target/pacts/)
 *   <li>Provider 側(wholesale サービス)はこの Pact ファイルを verify する別タスク (Provider verifier 試験 / Pact Broker
 *       連携)を将来追加する想定
 *   <li>本 MVP は Consumer 側だけで完結し、{@link WholesaleOrderPlacedMessage} に Pact が生成した payload を
 *       実際にデシリアライズできることまでを検証する
 * </ul>
 *
 * <p>互換性ポリシ: 必須フィールド({@code aggregateId / code / items[].lineNo / skuCode / locationId / quantity /
 * occurredAt}) を契約に含める。{@code partnerCode / currency / totalAmount / items[].unitPrice /
 * requestedDeliveryDate} は Provider 側で出すが Consumer は使わないため契約外(将来追加 OK)。 これにより Provider
 * 側のスキーマ進化が壊れにくくなる(consumer-driven の本質)。
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "wholesale", providerType = ProviderType.ASYNCH)
class WholesaleOrderPlacedConsumerPactTest {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());

    @Pact(consumer = "inventory-core")
    public V4Pact wholesaleOrderPlacedV1(PactBuilder builder) {
        PactDslJsonBody itemTemplate =
                new PactDslJsonBody()
                        .integerType("lineNo", 1)
                        .stringType("skuCode", "SKU-A")
                        .stringType("locationId", "LOC-1")
                        .integerType("quantity", 3);

        PactDslJsonBody payload =
                new PactDslJsonBody()
                        .numberType("aggregateId", 5001L)
                        .stringType("code", "SO-2026-0001")
                        .minArrayLike("items", 1, itemTemplate)
                        .stringMatcher(
                                "occurredAt",
                                "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z",
                                "2026-05-06T10:00:00Z");

        return builder.expectsToReceive(
                        "a wholesale order placed event", "core/interaction/message")
                .with(Map.of("message.contents", payload))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "wholesaleOrderPlacedV1", pactVersion = PactSpecVersion.V4)
    void inventoryCore_can_deserialize_wholesale_order_placed_payload(
            V4Interaction.AsynchronousMessage message) throws Exception {
        // Pact が生成した payload を inventory-core 側の DTO にデシリアライズ可能であることを保証。
        // unknown property は @JsonIgnoreProperties(ignoreUnknown = true) で吸収される。
        byte[] body = message.getContents().getContents().getValue();
        WholesaleOrderPlacedMessage dto =
                OBJECT_MAPPER.readValue(body, WholesaleOrderPlacedMessage.class);

        assertThat(dto.aggregateId()).isPositive();
        assertThat(dto.code()).isNotBlank();
        assertThat(dto.items()).isNotEmpty();
        WholesaleOrderPlacedMessage.Line line = dto.items().get(0);
        assertThat(line.lineNo()).isPositive();
        assertThat(line.skuCode()).isNotBlank();
        assertThat(line.locationId()).isNotBlank();
        assertThat(line.quantity()).isPositive();
        assertThat(dto.occurredAt()).isNotNull();
    }
}
