package com.example.inventory.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.example.inventory.core.InventoryCoreApplication;
import com.example.inventory.master.MasterDataApplication;

/**
 * Master Data → Inventory Core 連携 E2E。
 *
 * <p>検証フロー:
 *
 * <ol>
 *   <li>Master Data に POST /v1/skus で SKU を新規登録
 *   <li>Master Data の Outbox → Kafka {@code master.product.v1} → Inventory Core の SkuMasterListener
 *       → tenant_dev 配下の {@code sku_registry} テーブルに upsert
 *   <li>Inventory Core で同 SKU を参照する inventory レコードを引当 → 201
 *   <li>未登録 SKU を参照する inventory レコードでは引当が 422 ERR_UNKNOWN_SKU で弾かれる
 * </ol>
 *
 * <p>各サービスは別 Postgres データベース(同一コンテナ内)を使う。 同じ tenant スキーマ名({@code tenant_dev})を使うが、両サービスの {@code
 * outbox} テーブルが 衝突しないように DB を分けている(本番でも別 DB に同居しない設計、ADR-0005)。
 *
 * <p>JWT 検証はテスト用 {@link JwtDecoder} でバイパス。認証層自体は他 IT で検証済み。
 */
@Testcontainers(disabledWithoutDocker = true)
class EndToEndMasterDataInventoryFlowIT {

    private static final String TENANT_ID = "dev";
    private static final String TENANT_SCHEMA = "tenant_dev";
    private static final String SKU_CODE = "SKU-COCA-COLA-500ML";
    private static final long INVENTORY_ID_REGISTERED = 1L;
    private static final long INVENTORY_ID_UNKNOWN_SKU = 2L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("postgres")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    static ConfigurableApplicationContext masterDataCtx;
    static ConfigurableApplicationContext coreCtx;
    static int masterDataPort;
    static int corePort;
    static String masterDataUrl;
    static String coreUrl;

    static final RestTemplate REST = new RestTemplate();

    @BeforeAll
    static void setUp() throws Exception {
        // 1) Master Data と Inventory Core のための DB を別個に作成。
        try (Connection c = POSTGRES.createConnection("");
                Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE master_data");
            s.execute("CREATE DATABASE inventory_core");
        }
        masterDataUrl = baseJdbcUrl().replace("/postgres", "/master_data");
        coreUrl = baseJdbcUrl().replace("/postgres", "/inventory_core");

        // 2) 各 DB に tenant_dev スキーマを作り、当該サービスのマイグレーションを適用。
        prepareTenantSchema(masterDataUrl, "classpath:db/migration/master-data");
        prepareTenantSchema(coreUrl, "classpath:db/migration/inventory-core");

        // 3) Inventory Core 側の在庫レコード 2 件を直接シード。
        //    INVENTORY_ID_REGISTERED は SKU_CODE を参照(Master Data 経由で投影が来る予定)
        //    INVENTORY_ID_UNKNOWN_SKU は登録されない SKU を参照(422 シナリオ用)
        try (Connection c = directConnect(coreUrl);
                Statement s = c.createStatement()) {
            s.execute("SET search_path TO " + TENANT_SCHEMA);
            s.execute(
                    """
                    INSERT INTO inventory (id, sku_id, location_id, available, reserved, version)
                    VALUES (1, '%s', 'LOC-1', 10, 0, 1),
                           (2, 'SKU-NOT-IN-MASTER', 'LOC-1', 10, 0, 1)
                    """
                            .formatted(SKU_CODE));
        }

        // 4) Master Data 起動 — Outbox publisher を有効化、tenant=dev を毎秒ドレイン。
        masterDataCtx =
                new SpringApplicationBuilder(MasterDataApplication.class, TestSecurityConfig.class)
                        .web(WebApplicationType.SERVLET)
                        .properties(
                                merge(
                                        commonProps(),
                                        Map.of(
                                                "server.port", "0",
                                                "spring.datasource.url", masterDataUrl,
                                                "spring.flyway.enabled", "false",
                                                "platform.outbox.publisher-enabled", "true",
                                                "platform.outbox.poll-interval", "PT0.2S",
                                                "platform.outbox.tenants[0]", TENANT_ID,
                                                "platform.snowflake.worker-id", "0")))
                        .run();
        masterDataPort =
                masterDataCtx.getEnvironment().getProperty("local.server.port", Integer.class);

        // 5) Inventory Core 起動 — master.product.v1 を購読する SkuMasterListener が起動する。
        //    本テストでは Outbox 発行は不要のため publisher は無効化。
        coreCtx =
                new SpringApplicationBuilder(
                                InventoryCoreApplication.class, TestSecurityConfig.class)
                        .web(WebApplicationType.SERVLET)
                        .properties(
                                merge(
                                        commonProps(),
                                        Map.of(
                                                "server.port", "0",
                                                "spring.datasource.url", coreUrl,
                                                "spring.flyway.enabled", "false",
                                                "platform.outbox.publisher-enabled", "false",
                                                "platform.outbox.tenants[0]", TENANT_ID,
                                                "platform.snowflake.worker-id", "0")))
                        .run();
        corePort = coreCtx.getEnvironment().getProperty("local.server.port", Integer.class);
    }

    @AfterAll
    static void tearDown() {
        if (coreCtx != null) coreCtx.close();
        if (masterDataCtx != null) masterDataCtx.close();
    }

    @Test
    void MasterDataで作成したSKUがInventoryCoreの引当で使えるようになる() throws Exception {
        // 開始前: 投影テーブルにはまだ何も無い → 引当は 422 になるはず。
        assertReserveReturns(
                INVENTORY_ID_REGISTERED, HttpStatus.UNPROCESSABLE_ENTITY, "ERR_UNKNOWN_SKU");

        // 1. Master Data に SKU を新規登録(POST /v1/skus → 201)
        ResponseEntity<String> createResp =
                REST.exchange(
                        "http://localhost:" + masterDataPort + "/v1/skus",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                ("""
                        {"code":"%s","name":"コカ・コーラ 500ml","unitOfMeasure":"BOTTLE"}
                        """)
                                        .formatted(SKU_CODE),
                                jsonHeaders()),
                        String.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 2. master.product.v1 が Outbox → Kafka → Inventory Core の SkuMasterListener
        //    → tenant_dev.sku_registry に upsert される。投影が反映されるまで polling で引当を試行する。
        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(
                        () -> {
                            // 投影が来れば 201 が返る。来ていなければ 422 のまま。
                            ResponseEntity<String> resp = tryReserve(INVENTORY_ID_REGISTERED);
                            assertThat(resp.getStatusCode())
                                    .as("Master Data から投影が反映されたら 201 が返る")
                                    .isEqualTo(HttpStatus.CREATED);
                        });

        // 3. sku_registry に直接 SQL でも実体が入っていることを確認(投影更新の本体検証)
        try (Connection c = directConnect(coreUrl);
                Statement s = c.createStatement()) {
            s.execute("SET search_path TO " + TENANT_SCHEMA);
            try (var rs =
                    s.executeQuery(
                            "SELECT code, name, version FROM sku_registry WHERE code = '"
                                    + SKU_CODE
                                    + "'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("code")).isEqualTo(SKU_CODE);
                assertThat(rs.getString("name")).isEqualTo("コカ・コーラ 500ml");
                assertThat(rs.getLong("version")).isEqualTo(1L);
            }
        }
    }

    @Test
    void 投影に存在しないSKUを参照する在庫の引当は422_ERR_UNKNOWN_SKU() {
        // INVENTORY_ID_UNKNOWN_SKU は SKU-NOT-IN-MASTER を参照。Master Data に登録されていないので
        // 投影テーブルに行が来る可能性は無い。常に 422 のはず。
        assertReserveReturns(
                INVENTORY_ID_UNKNOWN_SKU, HttpStatus.UNPROCESSABLE_ENTITY, "ERR_UNKNOWN_SKU");
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void assertReserveReturns(
            long inventoryId, HttpStatus expectedStatus, String expectedErrorCode) {
        ResponseEntity<String> resp = tryReserve(inventoryId);
        assertThat(resp.getStatusCode()).isEqualTo(expectedStatus);
        if (expectedErrorCode != null) {
            assertThat(resp.getBody()).contains("\"errorCode\":\"" + expectedErrorCode + "\"");
        }
    }

    /** 4xx でも例外を投げず ResponseEntity を返す形にして、polling 用途に使う。 */
    private ResponseEntity<String> tryReserve(long inventoryId) {
        try {
            return REST.exchange(
                    "http://localhost:"
                            + corePort
                            + "/v1/inventories/"
                            + inventoryId
                            + "/reservations",
                    HttpMethod.POST,
                    new HttpEntity<>("{\"quantity\":1}", jsonHeaders()),
                    String.class);
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    private static String baseJdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    private static Map<String, Object> commonProps() {
        // 多重 Spring Context での HikariPool 競合を避けるため pool size を絞る。
        Map<String, Object> p = new HashMap<>();
        p.put("spring.datasource.username", POSTGRES.getUsername());
        p.put("spring.datasource.password", POSTGRES.getPassword());
        p.put("spring.datasource.hikari.maximum-pool-size", "5");
        p.put("spring.datasource.hikari.minimum-idle", "1");
        p.put("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers());
        p.put("spring.kafka.consumer.auto-offset-reset", "earliest");
        p.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", "");
        p.put("logging.level.org.apache.kafka", "WARN");
        p.put("logging.level.org.springframework.kafka", "WARN");
        p.put("logging.level.org.flywaydb", "WARN");
        return p;
    }

    private static Map<String, Object> merge(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Object> m = new HashMap<>(a);
        m.putAll(b);
        return m;
    }

    private static void prepareTenantSchema(String jdbcUrl, String migrationLocation)
            throws Exception {
        try (Connection c = directConnect(jdbcUrl);
                Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA IF NOT EXISTS " + TENANT_SCHEMA);
        }
        Flyway.configure()
                .dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(TENANT_SCHEMA)
                .locations(migrationLocation)
                .load()
                .migrate();
    }

    private static Connection directConnect(String jdbcUrl) throws Exception {
        return java.sql.DriverManager.getConnection(
                jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
        h.setBearerAuth("test-token");
        return h;
    }

    /** 両サービスの認証層をバイパスする共通テスト設定。トークンの値に関わらず 固定の Jwt(tenant_id=dev、INVENTORY_MANAGER ロール)を返す。 */
    @Configuration
    static class TestSecurityConfig {
        @Bean
        public JwtDecoder jwtDecoder() {
            return token ->
                    Jwt.withTokenValue(token)
                            .header("alg", "none")
                            .subject("e2e-test-user")
                            .claim("tenant_id", TENANT_ID)
                            .claim("roles", List.of("INVENTORY_MANAGER"))
                            .build();
        }
    }
}
