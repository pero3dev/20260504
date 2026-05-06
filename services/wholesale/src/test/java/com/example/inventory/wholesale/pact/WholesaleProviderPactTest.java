package com.example.inventory.wholesale.pact;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import com.example.inventory.wholesale.domain.event.SalesOrderPlacedEvent;
import com.example.inventory.wholesale.domain.event.SalesOrderShippedEvent;
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
 * Provider 側 Pact verifier 集約テスト(ADR-0019 Phase 2)。
 *
 * <p>{@code @Provider("wholesale")} に紐づく全 interaction を 1 クラスで verify する。 1 Provider
 * に対して複数のテストクラスを作ると Pact のクラス内 reflection 解決が崩れるため、 1 Provider = 1 テストクラスに集約する。
 *
 * <p>verify 対象:
 *
 * <ul>
 *   <li>{@code wholesale.order.placed.v1} — {@link SalesOrderPlacedEvent}
 *   <li>{@code wholesale.order.shipped.v1} — {@link SalesOrderShippedEvent}
 * </ul>
 *
 * <p>{@code @EnabledIfSystemProperty} で gating(`-Dpact.providerVerifier.enabled=true` で実行)。
 */
@Provider("wholesale")
@PactFolder("../inventory-core/target/pacts")
@EnabledIfSystemProperty(named = "pact.providerVerifier.enabled", matches = "true")
@ExtendWith(PactVerificationInvocationContextProvider.class)
public class WholesaleProviderPactTest {

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

    @PactVerifyProvider("a wholesale order placed event")
    public MessageAndMetadata wholesaleOrderPlacedV1Message() {
        SalesOrderPlacedEvent event =
                new SalesOrderPlacedEvent(
                        5001L,
                        "SO-2026-0001",
                        "PARTNER-ACME",
                        "JPY",
                        new BigDecimal("3000"),
                        List.of(
                                new SalesOrderPlacedEvent.Line(
                                        1, "SKU-A", "LOC-1", 3, new BigDecimal("1000"))),
                        LocalDate.of(2026, 6, 1),
                        Instant.parse("2026-05-06T10:00:00Z"));
        return serialize(event, "SalesOrderPlacedEvent");
    }

    @PactVerifyProvider("a wholesale sales order shipped event")
    public MessageAndMetadata salesOrderShippedV1Message() {
        Instant shippedAt = Instant.parse("2026-05-06T11:00:00Z");
        SalesOrderShippedEvent event =
                new SalesOrderShippedEvent(
                        5001L,
                        "SO-2026-0001",
                        "PARTNER-ACME",
                        List.of(new SalesOrderShippedEvent.Line(1, "SKU-A", "LOC-1", 3)),
                        shippedAt,
                        shippedAt);
        return serialize(event, "SalesOrderShippedEvent");
    }

    private static MessageAndMetadata serialize(Object event, String label) {
        try {
            byte[] body = OBJECT_MAPPER.writeValueAsBytes(event);
            return new MessageAndMetadata(body, Map.of());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(label + " をシリアライズできませんでした", e);
        }
    }
}
