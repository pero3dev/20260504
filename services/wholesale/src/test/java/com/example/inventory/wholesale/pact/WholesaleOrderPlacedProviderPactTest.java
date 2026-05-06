package com.example.inventory.wholesale.pact;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import com.example.inventory.wholesale.domain.event.SalesOrderPlacedEvent;
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
 * Provider 側 Pact verifier(ADR-0019 Phase 2.1)。
 *
 * <p>inventory-core が生成した pact
 * ファイル(`services/inventory-core/target/pacts/inventory-core-wholesale.json`) を読み込み、 wholesale
 * 側が出力する {@link SalesOrderPlacedEvent} の JSON 形式が consumer の契約を満たすか verify する。
 *
 * <p>実行前提:
 *
 * <ul>
 *   <li>先に inventory-core の Consumer test を実行して pact ファイルが生成されていること (`mvn -pl
 *       services/inventory-core test` または同 reactor verify)
 *   <li>本テスト単独実行時は `@EnabledIfSystemProperty` で gating(後述)
 * </ul>
 *
 * <p>本テストは reactor 全体ビルドで wholesale が build される時点で inventory-core が build 済みなら自動で動く。 inventory-core
 * test を skip した状態で wholesale を直接 verify すると pact ファイル不在で fail するため、 そのケース用に system property {@code
 * pact.providerVerifier.enabled=true} で gating している。
 *
 * <p>standalone 実行例:
 *
 * <pre>
 *   mvn -pl services/inventory-core test -Dtest=WholesaleOrderPlacedConsumerPactTest
 *   mvn -pl services/wholesale test -Dtest=WholesaleOrderPlacedProviderPactTest \
 *       -Dpact.providerVerifier.enabled=true
 * </pre>
 *
 * <p>将来 Pact Broker 連携(ADR-0019 Phase 3)時は {@link PactFolder} を {@code @PactBroker} に置換する。
 */
@Provider("wholesale")
@PactFolder("../inventory-core/target/pacts")
@EnabledIfSystemProperty(named = "pact.providerVerifier.enabled", matches = "true")
@ExtendWith(au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider.class)
class WholesaleOrderPlacedProviderPactTest {

    // Spring Boot 既定の ObjectMapper 動作を再現:
    //   - JavaTimeModule で Instant / LocalDate を扱う
    //   - WRITE_DATES_AS_TIMESTAMPS を disable して ISO-8601 文字列出力にする
    // (この設定が本番の OutboxKafkaSender と一致しないと、 contract verify が production
    //  代表性を失う)。
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void before(PactVerificationContext context) {
        // ASYNCH メッセージング contract なので MessageTestTarget を使う。
        context.setTarget(new MessageTestTarget());
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        // 各 interaction を verify。 失敗時は AssertionError が投げられて test fail。
        context.verifyInteraction();
    }

    /**
     * Consumer の {@code expectsToReceive("a wholesale order placed event", ...)} に対する Provider の
     * actual メッセージを返す。 wholesale 側で本来発行される {@link SalesOrderPlacedEvent} と同形式の JSON を生成する。
     *
     * <p>Provider の実装が consumer 契約に書いた必須フィールド(aggregateId / code / items[].* / occurredAt)を
     * すべて満たすことが verify 成功条件。 partnerCode 等 contract 外フィールドが含まれていても無視される (consumer が要求しないので)。
     */
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

        try {
            byte[] body = OBJECT_MAPPER.writeValueAsBytes(event);
            return new MessageAndMetadata(body, java.util.Map.of());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("SalesOrderPlacedEvent を JSON にシリアライズできませんでした", e);
        }
    }
}
