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
import au.com.dius.pact.provider.junitsupport.loader.PactBrokerConsumerVersionSelectors;
import au.com.dius.pact.provider.junitsupport.loader.SelectorBuilder;

/** Manufacturing Provider Pact verifier 共通基底(ADR-0019 Phase 3.5)。 */
@ExtendWith(PactVerificationInvocationContextProvider.class)
public class ManufacturingProviderPactBase {

    // Pact-JVM 4.6 は declaring class を newInstance() するため abstract 不可。
    // 詳細は WholesaleProviderPactBase 参照。

    /** Consumer version selectors(ADR-0019 Phase 5)。詳細は WholesaleProviderPactBase 参照。 */
    @PactBrokerConsumerVersionSelectors
    public static SelectorBuilder consumerVersionSelectors() {
        SelectorBuilder b = new SelectorBuilder().mainBranch().deployedOrReleased();
        String providerBranch = System.getProperty("pact.provider.branch");
        if (providerBranch != null && !providerBranch.isBlank() && !"main".equals(providerBranch)) {
            b.branch(providerBranch);
        }
        return b;
    }

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
                        // ADR-0019 Phase 4 で matching rule が pact JSON に正しく propagate されたため、
                        // minArrayLike(min=1) の意味どおり「1 件以上の任意の長さ」を Provider は返せる。
                        // 真の世界の WorkOrder は通常 BOM が複数あるので 2 件返してフィット度を高める。
                        List.of(
                                new WorkOrderReleasedEvent.Component("SKU-A", 20),
                                new WorkOrderReleasedEvent.Component("SKU-B", 5)),
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
