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
