package com.example.inventory.retail.pact;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import com.example.inventory.retail.domain.event.OrderPlacedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import au.com.dius.pact.provider.MessageAndMetadata;
import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;

/**
 * Provider 側 Pact verifier(ADR-0019 Phase 2.2)。
 *
 * <p>inventory-core が生成した {@code retail.order.placed.v1} の pact を verify。 詳細は {@code
 * services/wholesale/.../WholesaleOrderPlacedProviderPactTest} 参照。
 */
@Provider("retail-ec")
@PactFolder("../inventory-core/target/pacts")
@EnabledIfSystemProperty(named = "pact.providerVerifier.enabled", matches = "true")
@ExtendWith(PactVerificationInvocationContextProvider.class)
public class RetailOrderPlacedProviderPactTest {

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

    @PactVerifyProvider("a retail order placed event")
    public MessageAndMetadata retailOrderPlacedV1Message() {
        OrderPlacedEvent event =
                new OrderPlacedEvent(
                        6001L,
                        "ORD-2026-0001",
                        "alice@example.com",
                        "JPY",
                        new BigDecimal("3000"),
                        List.of(
                                new OrderPlacedEvent.Line(
                                        1, "SKU-A", "LOC-1", 2, new BigDecimal("1500"))),
                        Instant.parse("2026-05-06T10:00:00Z"));
        try {
            byte[] body = OBJECT_MAPPER.writeValueAsBytes(event);
            return new MessageAndMetadata(body, Map.of());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("OrderPlacedEvent をシリアライズできませんでした", e);
        }
    }
}
