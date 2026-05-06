package com.example.inventory.core.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.example.inventory.core.InventoryCoreApplication;

/**
 * inventory-core 単一 context の軽量 E2E。Postgres + Kafka 1 台ずつ立ち上げる。
 *
 * <p>e2e-tests モジュールの多重 Spring Context IT(CI 専用)とは別ライン。 ローカル開発でも Docker さえあれば動かせる軽量な IT として位置付ける。
 */
@SpringBootTest(
        classes = {InventoryCoreApplication.class, TestJwtDecoderConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "platform.outbox.publisher-enabled=true",
            "platform.outbox.poll-interval=PT0.2S",
            "platform.outbox.tenants[0]=dev",
            "spring.flyway.enabled=false",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
            "platform.snowflake.worker-id=0"
        })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class KafkaIntegrationE2ETest {

    private static final String TENANT_ID = "dev";
    private static final String TENANT_SCHEMA = "tenant_dev";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("inventory_core")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void wireProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @BeforeAll
    static void prepareSchema() throws Exception {
        try (Connection c = POSTGRES.createConnection("");
                Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA IF NOT EXISTS " + TENANT_SCHEMA);
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(TENANT_SCHEMA)
                .locations("classpath:db/migration/inventory-core")
                .load()
                .migrate();
    }

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;

    @BeforeEach
    void seed() throws Exception {
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            s.execute("SET search_path TO " + TENANT_SCHEMA);
            s.execute("TRUNCATE inventory");
            s.execute("TRUNCATE outbox");
            s.execute("TRUNCATE sku_registry");
            // SKU-1 は登録済み(在庫あり)、SKU-2 は登録なし(422 シナリオ用)。
            s.execute(
                    """
                    INSERT INTO inventory (id, sku_id, location_id, available, reserved, version)
                    VALUES (1, 'SKU-1', 'LOC-1', 10, 0, 1),
                           (2, 'SKU-2', 'LOC-1', 10, 0, 1)
                    """);
            s.execute(
                    """
                    INSERT INTO sku_registry (code, aggregate_id, name, unit_of_measure, version)
                    VALUES ('SKU-1', 1001, 'SKU-1 商品', 'PCS', 1)
                    """);
        }
    }

    @Test
    void unregisteredSku_returns422() throws Exception {
        mockMvc.perform(
                        post("/v1/inventories/{id}/reservations", 2L)
                                .with(
                                        jwt().jwt(
                                                        b ->
                                                                b.subject("user-001")
                                                                        .claim(
                                                                                "tenant_id",
                                                                                TENANT_ID)
                                                                        .claim(
                                                                                "roles",
                                                                                List.of(
                                                                                        "INVENTORY_MANAGER"))))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ERR_UNKNOWN_SKU"));
    }

    @Test
    void reserveSucceeds_andEventReachesKafka() throws Exception {
        mockMvc.perform(
                        post("/v1/inventories/{id}/reservations", 1L)
                                .with(
                                        jwt().jwt(
                                                        b ->
                                                                b.subject("user-001")
                                                                        .claim(
                                                                                "tenant_id",
                                                                                TENANT_ID)
                                                                        .claim(
                                                                                "roles",
                                                                                List.of(
                                                                                        "INVENTORY_MANAGER"))))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":3}"))
                .andExpect(status().isCreated());

        // Outbox→Kafka 経路を独立 consumer で観測する。
        try (KafkaConsumer<String, String> consumer = newTestConsumer()) {
            consumer.subscribe(List.of("inventory.movement.v1"));
            long deadline = System.currentTimeMillis() + 20_000;
            int found = 0;
            while (System.currentTimeMillis() < deadline && found == 0) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                found = records.count();
            }
            assertThat(found).as("inventory.movement.v1 が Kafka に発行されること").isPositive();
        }
    }

    @Test
    void wholesaleOrderPlacedThenShipped_drivesReserveThenShip() throws Exception {
        // Seed inventory(SKU-1, LOC-1, available=10, reserved=0)。
        // Wholesale から 2 つのイベントを送って reserve+ship のフローを確認する:
        //   1. wholesale.order.placed.v1 (qty=3) → reserve、reserved=3, available=7
        //   2. wholesale.order.shipped.v1 (qty=3) → ship、reserved=0, available=7
        long aggregateId = 5001L;
        String orderCode = "SO-IT-001";
        String placedPayload =
                """
                {"aggregateId":%d,"code":"%s","partnerCode":"PARTNER-X","currency":"JPY",\
                "totalAmount":3000,\
                "items":[{"lineNo":1,"skuCode":"SKU-1","locationId":"LOC-1","quantity":3,"unitPrice":1000}],\
                "occurredAt":"2026-05-06T10:00:00Z"}
                """
                        .formatted(aggregateId, orderCode);
        String shippedPayload =
                """
                {"aggregateId":%d,"code":"%s","partnerCode":"PARTNER-X",\
                "items":[{"lineNo":1,"skuCode":"SKU-1","locationId":"LOC-1","quantity":3}],\
                "shippedAt":"2026-05-06T11:00:00Z","occurredAt":"2026-05-06T11:00:00Z"}
                """
                        .formatted(aggregateId, orderCode);

        try (KafkaProducer<String, String> producer = newTestProducer()) {
            ProducerRecord<String, String> placed =
                    new ProducerRecord<>(
                            "wholesale.order.placed.v1", "dev:" + aggregateId, placedPayload);
            placed.headers().add(new RecordHeader("tenant_id", TENANT_ID.getBytes()));
            placed.headers().add(new RecordHeader("event_id", "100".getBytes()));
            producer.send(placed).get();
        }

        // Reserve が反映されるまで polling: reserved=3, available=7
        waitForInventory(1L, 7, 3);

        try (KafkaProducer<String, String> producer = newTestProducer()) {
            ProducerRecord<String, String> shipped =
                    new ProducerRecord<>(
                            "wholesale.order.shipped.v1", "dev:" + aggregateId, shippedPayload);
            shipped.headers().add(new RecordHeader("tenant_id", TENANT_ID.getBytes()));
            shipped.headers().add(new RecordHeader("event_id", "101".getBytes()));
            producer.send(shipped).get();
        }

        // Ship が反映されるまで polling: reserved=0, available=7(reserve 時の available 引きはそのまま)
        waitForInventory(1L, 7, 0);
    }

    @Test
    void wholesaleOrderPlacedThenCancelled_drivesReserveThenRelease() throws Exception {
        // Seed inventory(SKU-1, LOC-1, available=10, reserved=0)。
        // Wholesale 取消フロー: Place → Reserve → Cancel → Release で reserved が戻ること。
        //   1. wholesale.order.placed.v1 (qty=3) → reserve、reserved=3, available=7
        //   2. wholesale.order.cancelled.v1 (qty=3) → release、reserved=0, available=10
        long aggregateId = 5101L;
        String orderCode = "SO-IT-101";
        String placedPayload =
                """
                {"aggregateId":%d,"code":"%s","partnerCode":"PARTNER-X","currency":"JPY",\
                "totalAmount":3000,\
                "items":[{"lineNo":1,"skuCode":"SKU-1","locationId":"LOC-1","quantity":3,"unitPrice":1000}],\
                "occurredAt":"2026-05-06T10:00:00Z"}
                """
                        .formatted(aggregateId, orderCode);
        String cancelledPayload =
                """
                {"aggregateId":%d,"code":"%s","partnerCode":"PARTNER-X",\
                "items":[{"lineNo":1,"skuCode":"SKU-1","locationId":"LOC-1","quantity":3}],\
                "cancelledAt":"2026-05-06T12:00:00Z","occurredAt":"2026-05-06T12:00:00Z"}
                """
                        .formatted(aggregateId, orderCode);

        try (KafkaProducer<String, String> producer = newTestProducer()) {
            ProducerRecord<String, String> placed =
                    new ProducerRecord<>(
                            "wholesale.order.placed.v1", "dev:" + aggregateId, placedPayload);
            placed.headers().add(new RecordHeader("tenant_id", TENANT_ID.getBytes()));
            placed.headers().add(new RecordHeader("event_id", "200".getBytes()));
            producer.send(placed).get();
        }

        waitForInventory(1L, 7, 3);

        try (KafkaProducer<String, String> producer = newTestProducer()) {
            ProducerRecord<String, String> cancelled =
                    new ProducerRecord<>(
                            "wholesale.order.cancelled.v1", "dev:" + aggregateId, cancelledPayload);
            cancelled.headers().add(new RecordHeader("tenant_id", TENANT_ID.getBytes()));
            cancelled.headers().add(new RecordHeader("event_id", "201".getBytes()));
            producer.send(cancelled).get();
        }

        // Release が反映されるまで polling: reserved=0, available=10(完全に元に戻る)
        waitForInventory(1L, 10, 0);
    }

    @Test
    void retailOrderPlacedThenShipped_drivesReserveThenShip() throws Exception {
        // Retail/EC 出荷フロー: Place → Reserve → Ship。
        //   1. retail.order.placed.v1 (qty=4) → reserved=4, available=6
        //   2. retail.order.shipped.v1 (qty=4) → ship、reserved=0, available=6
        //      (ship では available は変わらない、reserve 時に既に引いてあるため)
        long aggregateId = 6101L;
        String orderCode = "ORD-IT-101";
        String placedPayload =
                """
                {"aggregateId":%d,"code":"%s","customerEmail":"alice@example.com","currency":"JPY",\
                "totalAmount":4000,\
                "items":[{"lineNo":1,"skuCode":"SKU-1","locationId":"LOC-1","quantity":4,"unitPrice":1000}],\
                "occurredAt":"2026-05-06T10:00:00Z"}
                """
                        .formatted(aggregateId, orderCode);
        String shippedPayload =
                """
                {"aggregateId":%d,"code":"%s","customerEmail":"alice@example.com",\
                "items":[{"lineNo":1,"skuCode":"SKU-1","locationId":"LOC-1","quantity":4}],\
                "shippedAt":"2026-05-06T11:00:00Z","occurredAt":"2026-05-06T11:00:00Z"}
                """
                        .formatted(aggregateId, orderCode);

        try (KafkaProducer<String, String> producer = newTestProducer()) {
            ProducerRecord<String, String> placed =
                    new ProducerRecord<>(
                            "retail.order.placed.v1", "dev:" + aggregateId, placedPayload);
            placed.headers().add(new RecordHeader("tenant_id", TENANT_ID.getBytes()));
            placed.headers().add(new RecordHeader("event_id", "400".getBytes()));
            producer.send(placed).get();
        }

        waitForInventory(1L, 6, 4);

        try (KafkaProducer<String, String> producer = newTestProducer()) {
            ProducerRecord<String, String> shipped =
                    new ProducerRecord<>(
                            "retail.order.shipped.v1", "dev:" + aggregateId, shippedPayload);
            shipped.headers().add(new RecordHeader("tenant_id", TENANT_ID.getBytes()));
            shipped.headers().add(new RecordHeader("event_id", "401".getBytes()));
            producer.send(shipped).get();
        }

        // ship 完了: reserved=0, available=6(ship では available は変わらない)
        waitForInventory(1L, 6, 0);
    }

    @Test
    void retailOrderPlacedThenCancelled_drivesReserveThenRelease() throws Exception {
        // Retail/EC 取消フロー: Place → Reserve → Cancel → Release で reserved が戻ること。
        //   1. retail.order.placed.v1 (qty=4) → reserve、reserved=4, available=6
        //   2. retail.order.cancelled.v1 (qty=4) → release、reserved=0, available=10
        long aggregateId = 6201L;
        String orderCode = "ORD-IT-201";
        String placedPayload =
                """
                {"aggregateId":%d,"code":"%s","customerEmail":"alice@example.com","currency":"JPY",\
                "totalAmount":4000,\
                "items":[{"lineNo":1,"skuCode":"SKU-1","locationId":"LOC-1","quantity":4,"unitPrice":1000}],\
                "occurredAt":"2026-05-06T10:00:00Z"}
                """
                        .formatted(aggregateId, orderCode);
        String cancelledPayload =
                """
                {"aggregateId":%d,"code":"%s","customerEmail":"alice@example.com",\
                "items":[{"lineNo":1,"skuCode":"SKU-1","locationId":"LOC-1","quantity":4}],\
                "cancelledAt":"2026-05-06T12:00:00Z","occurredAt":"2026-05-06T12:00:00Z"}
                """
                        .formatted(aggregateId, orderCode);

        try (KafkaProducer<String, String> producer = newTestProducer()) {
            ProducerRecord<String, String> placed =
                    new ProducerRecord<>(
                            "retail.order.placed.v1", "dev:" + aggregateId, placedPayload);
            placed.headers().add(new RecordHeader("tenant_id", TENANT_ID.getBytes()));
            placed.headers().add(new RecordHeader("event_id", "300".getBytes()));
            producer.send(placed).get();
        }

        waitForInventory(1L, 6, 4);

        try (KafkaProducer<String, String> producer = newTestProducer()) {
            ProducerRecord<String, String> cancelled =
                    new ProducerRecord<>(
                            "retail.order.cancelled.v1", "dev:" + aggregateId, cancelledPayload);
            cancelled.headers().add(new RecordHeader("tenant_id", TENANT_ID.getBytes()));
            cancelled.headers().add(new RecordHeader("event_id", "301".getBytes()));
            producer.send(cancelled).get();
        }

        waitForInventory(1L, 10, 0);
    }

    @Test
    void masterProductV1_isProjectedToSkuRegistry() throws Exception {
        // Kafka に master.product.v1 をテスト producer で送り、SkuMasterListener が
        // tenant_dev.sku_registry に upsert することを確認する。
        String code = "SKU-PROJECTED-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String payload =
                """
                {"aggregateId":2002,"code":"%s","name":"投影テスト","description":"",\
                "unitOfMeasure":"PCS","versionAfter":1,"occurredAt":"2026-05-05T00:00:00Z"}
                """
                        .formatted(code);

        try (KafkaProducer<String, String> producer = newTestProducer()) {
            ProducerRecord<String, String> rec =
                    new ProducerRecord<>("master.product.v1", "dev:2002", payload);
            rec.headers().add(new RecordHeader("tenant_id", TENANT_ID.getBytes()));
            producer.send(rec).get();
        }

        // SkuMasterListener → SkuRegistryRepositoryImpl による upsert を polling で待つ。
        long deadline = System.currentTimeMillis() + 20_000;
        boolean found = false;
        while (System.currentTimeMillis() < deadline && !found) {
            try (Connection c = dataSource.getConnection();
                    Statement s = c.createStatement()) {
                s.execute("SET search_path TO " + TENANT_SCHEMA);
                try (var rs =
                        s.executeQuery(
                                "SELECT count(*) FROM sku_registry WHERE code = '" + code + "'")) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        found = true;
                        break;
                    }
                }
            }
            Thread.sleep(300);
        }
        assertThat(found).as("master.product.v1 が sku_registry に投影されること").isTrue();
    }

    /** {@code inventory.id == id} の available / reserved が期待値に到達するまで polling 待機(最大 20 秒)。 */
    private void waitForInventory(long id, int expectedAvailable, int expectedReserved)
            throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            try (Connection c = dataSource.getConnection();
                    Statement s = c.createStatement()) {
                s.execute("SET search_path TO " + TENANT_SCHEMA);
                try (var rs =
                        s.executeQuery(
                                "SELECT available, reserved FROM inventory WHERE id = " + id)) {
                    if (rs.next()) {
                        int available = rs.getInt(1);
                        int reserved = rs.getInt(2);
                        if (available == expectedAvailable && reserved == expectedReserved) {
                            return;
                        }
                    }
                }
            }
            Thread.sleep(300);
        }
        throw new AssertionError(
                "Inventory(id="
                        + id
                        + ") が期待値 (available="
                        + expectedAvailable
                        + ", reserved="
                        + expectedReserved
                        + ") に到達せずタイムアウト");
    }

    private static KafkaConsumer<String, String> newTestConsumer() {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-it-" + UUID.randomUUID());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(p);
    }

    private static KafkaProducer<String, String> newTestProducer() {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(p);
    }
}
