package com.example.inventory.manufacturing.pact;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import com.example.inventory.manufacturing.domain.event.WorkOrderReleasedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import au.com.dius.pact.provider.MessageAndMetadata;
import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;

/** Manufacturing Provider Pact verifier 共通基底(ADR-0019 Phase 3.5)。 */
@ExtendWith(PactVerificationInvocationContextProvider.class)
public class ManufacturingProviderPactBase {

    // Pact-JVM 4.6 は declaring class を newInstance() するため abstract 不可。
    // 詳細は WholesaleProviderPactBase 参照。

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void before(PactVerificationContext context) {
        context.setTarget(new MessageTestTarget());
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @PactVerifyProvider("a manufacturing work order released event")
    public MessageAndMetadata workOrderReleasedV1Message() {
        Instant occurredAt = Instant.parse("2026-05-06T10:00:00Z");
        WorkOrderReleasedEvent event =
                new WorkOrderReleasedEvent(
                        7001L,
                        "WO-2026-0001",
                        "SKU-WIDGET-X",
                        "LOC-FACTORY-A",
                        10,
                        // Consumer は minArrayLike(min=1) を指定しているが Pact-JVM 4.6 はリスト長を
                        // デフォルトで厳密一致させる。 Provider は 1 要素返すことで契約を満たす。
                        // (ADR-0019 Phase 4 候補で LambdaDsl 全面移行時に matching rule を flexible 化する)
                        List.of(new WorkOrderReleasedEvent.Component("SKU-A", 20)),
                        LocalDate.of(2026, 5, 7),
                        occurredAt);
        try {
            byte[] body = OBJECT_MAPPER.writeValueAsBytes(event);
            return new MessageAndMetadata(body, Map.of());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("WorkOrderReleasedEvent をシリアライズできませんでした", e);
        }
    }
}
