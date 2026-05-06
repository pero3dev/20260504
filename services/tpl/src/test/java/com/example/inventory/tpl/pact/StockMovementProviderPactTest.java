package com.example.inventory.tpl.pact;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import com.example.inventory.tpl.domain.event.StockMovementPlannedEvent;
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

/** Provider 側 Pact verifier — {@code tpl.stock.movement.v1}(ADR-0019 Phase 2.2)。 */
@Provider("tpl")
@PactFolder("../inventory-core/target/pacts")
@EnabledIfSystemProperty(named = "pact.providerVerifier.enabled", matches = "true")
@ExtendWith(PactVerificationInvocationContextProvider.class)
public class StockMovementProviderPactTest {

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

    @PactVerifyProvider("a tpl stock movement event")
    public MessageAndMetadata stockMovementV1Message() {
        StockMovementPlannedEvent event =
                new StockMovementPlannedEvent(
                        9001L,
                        "MV-2026-0001",
                        "PARTNER-3PL",
                        "SKU-A",
                        "LOC-3PL-A",
                        "INBOUND",
                        50,
                        "REF-001",
                        1L,
                        Instant.parse("2026-05-06T10:00:00Z"));
        try {
            byte[] body = OBJECT_MAPPER.writeValueAsBytes(event);
            return new MessageAndMetadata(body, Map.of());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("StockMovementPlannedEvent をシリアライズできませんでした", e);
        }
    }
}
