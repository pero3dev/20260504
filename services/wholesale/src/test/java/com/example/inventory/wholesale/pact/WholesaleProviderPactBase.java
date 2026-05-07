package com.example.inventory.wholesale.pact;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
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
import au.com.dius.pact.provider.junitsupport.loader.PactBrokerConsumerVersionSelectors;
import au.com.dius.pact.provider.junitsupport.loader.SelectorBuilder;

/**
 * Wholesale Provider Pact verifier 共通基底(ADR-0019 Phase 3.5)。
 *
 * <p>{@code @PactVerifyProvider} メソッドと message のシリアライザを保持。 サブクラスで {@code @Provider} と source
 * (Folder / Broker)を annotation 付与する。
 */
@ExtendWith(PactVerificationInvocationContextProvider.class)
public class WholesaleProviderPactBase {

    // ⚠️ Pact-JVM 4.6 は @PactVerifyProvider メソッドを呼ぶ際に declaring class を no-arg
    // newInstance() するため、 abstract にはできない。 Folder / Broker source 切替えのために
    // 継承させているが、 base 自体も instantiable に保つ必要がある。
    // surefire の include パターン (**/*Test.java) には Base が含まれないので
    // テストとしては実行されない。

    /**
     * Consumer version selectors(ADR-0019 Phase 5)。 Broker から取得する Consumer pact のバージョンを限定する。
     *
     * <ul>
     *   <li>{@code mainBranch()} — main の最新 pact(プロダクション safety net)
     *   <li>{@code deployedOrReleased()} — 各 environment に現在 deploy 済みの pact(後方互換 safety)
     *   <li>{@code branch(provider branch)} — Provider と同じ branch identifier の Consumer pact(PR
     *       連動)。 Pact-JVM 4.6 の {@code matchingBranch()} は Broker request body に {@code
     *       providerVersionBranch} を自動付与しないため 400 になる(2026-04 時点の既知バグ)。 回避策として、 {@code
     *       pact.provider.branch} system property の値で明示的に branch を指定する。
     * </ul>
     *
     * Folder source({@code @PactFolder})経路では本メソッドは無視される(Broker 専用)。
     */
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
