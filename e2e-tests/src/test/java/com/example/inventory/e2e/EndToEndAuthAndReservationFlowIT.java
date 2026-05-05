package com.example.inventory.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.example.inventory.audit.AuditServiceApplication;
import com.example.inventory.core.InventoryCoreApplication;
import com.example.inventory.identity.IdentityBrokerApplication;
import com.example.inventory.readmodel.InventoryReadModelApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 4サービス連結 E2E:認証(identity-broker)→ 引当(inventory-core)→ 投影(inventory-read-model) →
 * 監査チェーン(audit-service)。
 *
 * <p>本テストは以下を一気通貫で確認する:
 *
 * <ol>
 *   <li>identity-broker にログイン → セッショントークン取得
 *   <li>テナント選択 → アクセストークン(JWT)取得
 *   <li>inventory-core が identity-broker の JWKS でトークンを検証 → 引当成功
 *   <li>イベントが Outbox → Kafka → inventory-read-model へ伝播
 *   <li>inventory-read-model が同じ JWKS でトークン検証 → 投影 GET が更新後値を返す
 *   <li>audit.log.v1 に監査イベントが流れる(@AuditMask によるパスワード/JWTマスク確認)
 *   <li>audit-service が消費して audit_record にハッシュチェーンを構築
 * </ol>
 *
 * <p>JWT バイパスをしない「本物」の認証経路。Identity Broker の JWKS エンドポイントが 下流サービスから到達可能な点が肝。
 */
@Testcontainers(disabledWithoutDocker = true)
class EndToEndAuthAndReservationFlowIT {

    private static final String INVENTORY_TENANT = "dev";
    private static final String INVENTORY_TENANT_SCHEMA = "tenant_dev";
    private static final String AUDIT_SCHEMA = "audit_service";
    private static final long TEST_INVENTORY_ID = 1L;
    private static final String TEST_USER_EMAIL = "alice@example.com";
    private static final String TEST_USER_PASSWORD = "test-password-123";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("platform")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static final RestTemplate REST = new RestTemplate();

    static ConfigurableApplicationContext identityCtx;
    static ConfigurableApplicationContext coreCtx;
    static ConfigurableApplicationContext readModelCtx;
    static ConfigurableApplicationContext auditCtx;
    static int identityPort;
    static int corePort;
    static int readModelPort;
    static int auditPort;

    /** audit.log.v1 を購読する独立コンシューマ。テスト中の audit イベント出力を観測する。 */
    static KafkaConsumer<String, String> auditConsumer;

    static Thread auditConsumerThread;
    static volatile boolean auditConsumerRunning = true;
    static final BlockingQueue<AuditCapture> AUDIT_EVENTS = new LinkedBlockingQueue<>();

    record AuditCapture(
            String tenantHeader,
            String action,
            String targetType,
            String outcome,
            String operatorTenantId,
            String operatorUserId,
            String inputJson) {}

    @BeforeAll
    static void setUp() throws Exception {
        prepareIdentitySchema();
        prepareInventorySchema();
        prepareAuditSchema();
        seedIdentityUser();

        // Identity Broker 起動(JWT 発行側)。
        // 本テストでは audit イベントが Kafka まで流れることも検証するため、
        // outbox publisher を有効化してドレイン頻度も上げる。
        identityCtx =
                new SpringApplicationBuilder(IdentityBrokerApplication.class)
                        .web(WebApplicationType.SERVLET)
                        .properties(commonProps())
                        .properties(
                                Map.of(
                                        "server.port", "0",
                                        "spring.flyway.enabled", "false",
                                        "platform.identity.issuer", "http://localhost:0/",
                                        "platform.outbox.publisher-enabled", "true",
                                        "platform.outbox.poll-interval", "PT0.2S"))
                        .run();
        identityPort = identityCtx.getEnvironment().getProperty("local.server.port", Integer.class);

        String jwksUri = "http://localhost:" + identityPort + "/.well-known/jwks.json";

        // Inventory Core 起動(JWT 検証側 + 書込権威)
        coreCtx =
                new SpringApplicationBuilder(InventoryCoreApplication.class)
                        .web(WebApplicationType.SERVLET)
                        .properties(commonProps())
                        .properties(
                                Map.of(
                                        "server.port", "0",
                                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                                                jwksUri,
                                        "spring.security.oauth2.resourceserver.jwt.issuer-uri", "",
                                        "platform.outbox.publisher-enabled", "true",
                                        "platform.outbox.poll-interval", "PT0.2S",
                                        "platform.outbox.tenants[0]", INVENTORY_TENANT))
                        .run();
        corePort = coreCtx.getEnvironment().getProperty("local.server.port", Integer.class);

        // Inventory Read Model 起動(JWT 検証側 + Kafka 投影)
        readModelCtx =
                new SpringApplicationBuilder(InventoryReadModelApplication.class)
                        .web(WebApplicationType.SERVLET)
                        .properties(commonProps())
                        .properties(
                                Map.of(
                                        "server.port",
                                        "0",
                                        "spring.data.redis.host",
                                        REDIS.getHost(),
                                        "spring.data.redis.port",
                                        REDIS.getMappedPort(6379).toString(),
                                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                                        jwksUri,
                                        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                                        "",
                                        "spring.kafka.consumer.auto-offset-reset",
                                        "earliest"))
                        .run();
        readModelPort =
                readModelCtx.getEnvironment().getProperty("local.server.port", Integer.class);

        // Audit Service 起動(Kafka 全消費 → ハッシュチェーン → public.audit_record とは別の audit_service スキーマへ書込)。
        // currentSchema パラメータで search_path をスキーマに固定する(Pool 方式)。
        auditCtx =
                new SpringApplicationBuilder(AuditServiceApplication.class)
                        .web(WebApplicationType.SERVLET)
                        .properties(commonProps())
                        .properties(
                                Map.of(
                                        "server.port", "0",
                                        "spring.datasource.url",
                                                POSTGRES.getJdbcUrl()
                                                        + "?currentSchema="
                                                        + AUDIT_SCHEMA,
                                        "spring.flyway.enabled", "false",
                                        "spring.kafka.consumer.auto-offset-reset", "earliest"))
                        .run();
        auditPort = auditCtx.getEnvironment().getProperty("local.server.port", Integer.class);

        startAuditConsumer();
    }

    @AfterAll
    static void tearDown() {
        stopAuditConsumer();
        if (auditCtx != null) auditCtx.close();
        if (readModelCtx != null) readModelCtx.close();
        if (coreCtx != null) coreCtx.close();
        if (identityCtx != null) identityCtx.close();
    }

    @Test
    void フルフロー_ログイン_テナント選択_引当_投影確認() throws Exception {
        // 1. ログイン
        ResponseEntity<String> loginResp =
                REST.exchange(
                        "http://localhost:" + identityPort + "/v1/auth/sessions",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                "{\"email\":\""
                                        + TEST_USER_EMAIL
                                        + "\",\"password\":\""
                                        + TEST_USER_PASSWORD
                                        + "\"}",
                                jsonHeaders()),
                        String.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode loginBody = OBJECT_MAPPER.readTree(loginResp.getBody());
        String sessionToken = loginBody.get("sessionToken").asText();
        assertThat(sessionToken).isNotBlank();
        assertThat(loginBody.get("accessibleTenants").get(0).get("tenantId").asText())
                .isEqualTo(INVENTORY_TENANT);

        // 2. テナント選択 → アクセストークン
        ResponseEntity<String> selectResp =
                REST.exchange(
                        "http://localhost:" + identityPort + "/v1/auth/tenant-sessions",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                "{\"sessionToken\":\""
                                        + sessionToken
                                        + "\",\"tenantId\":\""
                                        + INVENTORY_TENANT
                                        + "\"}",
                                jsonHeaders()),
                        String.class);
        assertThat(selectResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode selectBody = OBJECT_MAPPER.readTree(selectResp.getBody());
        String accessToken = selectBody.get("accessToken").asText();
        assertThat(accessToken).isNotBlank();

        // 3. inventory-core に引当リクエスト(本物の JWT を Authorization ヘッダで送る)
        ResponseEntity<String> reserveResp =
                REST.exchange(
                        "http://localhost:"
                                + corePort
                                + "/v1/inventories/"
                                + TEST_INVENTORY_ID
                                + "/reservations",
                        HttpMethod.POST,
                        new HttpEntity<>("{\"quantity\":3}", bearer(accessToken)),
                        String.class);
        assertThat(reserveResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 4. inventory-read-model に GET → 投影が反映されるまで待つ
        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(
                        () -> {
                            ResponseEntity<String> getResp =
                                    REST.exchange(
                                            "http://localhost:"
                                                    + readModelPort
                                                    + "/v1/inventories/"
                                                    + TEST_INVENTORY_ID,
                                            HttpMethod.GET,
                                            new HttpEntity<>(bearer(accessToken)),
                                            String.class);
                            assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
                            String body = getResp.getBody();
                            assertThat(body).contains("\"available\":7");
                            assertThat(body).contains("\"reserved\":3");
                            assertThat(body).contains("\"version\":2");
                        });
    }

    @Test
    void 不正パスワードは401() {
        try {
            REST.exchange(
                    "http://localhost:" + identityPort + "/v1/auth/sessions",
                    HttpMethod.POST,
                    new HttpEntity<>(
                            "{\"email\":\"" + TEST_USER_EMAIL + "\",\"password\":\"wrong\"}",
                            jsonHeaders()),
                    String.class);
            throw new AssertionError("401 should have been thrown");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(e.getResponseBodyAsString()).contains("ERR_AUTHENTICATION_FAILED");
        }
    }

    @Test
    void 監査イベントが_audit_log_v1_に4種類流れる() throws Exception {
        // 1〜3 は フルフロー成功テストと同じ操作。本テスト独立にすると更に時間がかかるため、
        // 同じシーケンスを実行しつつ Kafka コンシューマで観測する。
        ResponseEntity<String> loginResp =
                REST.exchange(
                        "http://localhost:" + identityPort + "/v1/auth/sessions",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                "{\"email\":\""
                                        + TEST_USER_EMAIL
                                        + "\",\"password\":\""
                                        + TEST_USER_PASSWORD
                                        + "\"}",
                                jsonHeaders()),
                        String.class);
        String sessionToken =
                OBJECT_MAPPER.readTree(loginResp.getBody()).get("sessionToken").asText();

        ResponseEntity<String> selectResp =
                REST.exchange(
                        "http://localhost:" + identityPort + "/v1/auth/tenant-sessions",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                "{\"sessionToken\":\""
                                        + sessionToken
                                        + "\",\"tenantId\":\""
                                        + INVENTORY_TENANT
                                        + "\"}",
                                jsonHeaders()),
                        String.class);
        String accessToken =
                OBJECT_MAPPER.readTree(selectResp.getBody()).get("accessToken").asText();

        // 引当 (inventory-core)
        REST.exchange(
                "http://localhost:"
                        + corePort
                        + "/v1/inventories/"
                        + TEST_INVENTORY_ID
                        + "/reservations",
                HttpMethod.POST,
                new HttpEntity<>("{\"quantity\":1}", bearer(accessToken)),
                String.class);

        // 投影が出るまで待ってから GET (read-model)
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(
                        () -> {
                            ResponseEntity<String> getResp =
                                    REST.exchange(
                                            "http://localhost:"
                                                    + readModelPort
                                                    + "/v1/inventories/"
                                                    + TEST_INVENTORY_ID,
                                            HttpMethod.GET,
                                            new HttpEntity<>(bearer(accessToken)),
                                            String.class);
                            assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
                        });

        // 期待する4種の audit イベントが Kafka に流れるまで待機。
        // - identity-broker: USER_AUTHENTICATE (TenantContext 空 → SYSTEM フォールバック →
        // tenant_id="platform")
        // - identity-broker: USER_SELECT_TENANT (同上、targetId はテナントID)
        // - inventory-core:  INVENTORY_RESERVE (TenantContext = "dev" → tenant_id="dev"、Outbox 経由)
        // - read-model:      INVENTORY_QUERY   (TenantContext = "dev" → tenant_id="dev"、DirectKafka
        // 経由)
        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(
                        () -> {
                            assertThat(AUDIT_EVENTS.stream().map(AuditCapture::action).toList())
                                    .contains(
                                            "USER_AUTHENTICATE",
                                            "USER_SELECT_TENANT",
                                            "INVENTORY_RESERVE",
                                            "INVENTORY_QUERY");
                        });

        AuditCapture login = pickAction("USER_AUTHENTICATE");
        assertThat(login.tenantHeader()).isEqualTo("platform"); // SYSTEM フォールバック
        assertThat(login.outcome()).isEqualTo("SUCCESS");
        assertThat(login.operatorTenantId()).isEqualTo("unknown"); // JWT 確立前
        // パスワードがマスクされていること(@AuditMask 検証 / J-SOX 観点)
        assertThat(login.inputJson()).contains("\"email\":\"" + TEST_USER_EMAIL + "\"");
        assertThat(login.inputJson()).contains("\"password\":\"***\"");
        assertThat(login.inputJson()).doesNotContain(TEST_USER_PASSWORD);

        AuditCapture select = pickAction("USER_SELECT_TENANT");
        assertThat(select.tenantHeader()).isEqualTo("platform");
        assertThat(select.outcome()).isEqualTo("SUCCESS");
        // セッショントークン(JWT)もマスクされていること
        assertThat(select.inputJson()).contains("\"sessionToken\":\"***\"");
        assertThat(select.inputJson()).contains("\"tenantId\":\"" + INVENTORY_TENANT + "\"");

        AuditCapture reserve = pickAction("INVENTORY_RESERVE");
        assertThat(reserve.tenantHeader()).isEqualTo("dev"); // 通常の TenantContext 由来
        assertThat(reserve.outcome()).isEqualTo("SUCCESS");
        assertThat(reserve.operatorTenantId()).isEqualTo("dev");

        AuditCapture query = pickAction("INVENTORY_QUERY");
        assertThat(query.tenantHeader()).isEqualTo("dev");
        assertThat(query.targetType()).isEqualTo("Inventory");

        // === audit-service が DB へハッシュチェーン化して永続化していることを検証 ===
        await().atMost(Duration.ofSeconds(25))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(
                        () -> {
                            List<AuditRow> records = readAuditRecords();

                            // 期待アクションが揃うまで待つ(audit-service が Kafka を消化するのを待つ)
                            assertThat(records.stream().map(AuditRow::action).toList())
                                    .contains(
                                            "USER_AUTHENTICATE",
                                            "USER_SELECT_TENANT",
                                            "INVENTORY_RESERVE",
                                            "INVENTORY_QUERY");

                            // テナントごとにチェーン整合性を検証(prev_hash[i] == hash[i-1]、先頭 prev_hash は INITIAL)
                            verifyChainIntegrity(
                                    records.stream()
                                            .filter(r -> "platform".equals(r.tenantId()))
                                            .toList());
                            verifyChainIntegrity(
                                    records.stream()
                                            .filter(r -> "dev".equals(r.tenantId()))
                                            .toList());
                        });

        // === audit-service の /admin/audit-chain/verify エンドポイントによる
        //     SHA-256 再計算ベースのチェーン検証(改竄検知の本体ロジック)===
        verifyChainViaAdminEndpoint("platform");
        verifyChainViaAdminEndpoint("dev");

        // === 改竄検出テスト: audit_record を直接 UPDATE してフィールド値を変える。
        //     hash は再計算されないため、Sha256HashCalculator で再計算すると一致せず
        //     HASH_MISMATCH が検出されることを実証する(J-SOX 監査説明用の核心テスト)===
        String originalAction = "INVENTORY_RESERVE";
        tamperAuditRecordAction("dev", 1L, "TAMPERED_BY_TEST");
        try {
            ResponseEntity<String> mismatchResp =
                    REST.exchange(
                            "http://localhost:"
                                    + auditPort
                                    + "/admin/audit-chain/verify?tenant=dev",
                            HttpMethod.GET,
                            new HttpEntity<>(jsonHeaders()),
                            String.class);
            assertThat(mismatchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode mismatchBody = OBJECT_MAPPER.readTree(mismatchResp.getBody());

            assertThat(mismatchBody.get("status").asText())
                    .as("改竄後の検証は MISMATCH を期待")
                    .isEqualTo("MISMATCH");
            assertThat(mismatchBody.get("mismatches").size()).isPositive();
            assertThat(containsMismatch(mismatchBody.get("mismatches"), "HASH_MISMATCH", 1L))
                    .as("sequence=1 で HASH_MISMATCH が検出されること")
                    .isTrue();
        } finally {
            // 後続テストへの汚染を避けるため元に戻す
            tamperAuditRecordAction("dev", 1L, originalAction);
        }
    }

    /** audit_record の特定行の action を書き換え(改竄シミュレーション/復旧両方に使用)。 */
    private static void tamperAuditRecordAction(String tenantId, long sequence, String action)
            throws Exception {
        try (Connection c = POSTGRES.createConnection("");
                java.sql.PreparedStatement ps = prepareUpdate(c, tenantId, sequence, action)) {
            int affected = ps.executeUpdate();
            assertThat(affected).as("UPDATE 対象行が存在すること").isEqualTo(1);
        }
    }

    private static java.sql.PreparedStatement prepareUpdate(
            Connection c, String tenantId, long sequence, String action) throws Exception {
        try (Statement s = c.createStatement()) {
            s.execute("SET search_path TO " + AUDIT_SCHEMA);
        }
        java.sql.PreparedStatement ps =
                c.prepareStatement(
                        "UPDATE audit_record SET action = ? WHERE tenant_id = ? AND sequence = ?");
        ps.setString(1, action);
        ps.setString(2, tenantId);
        ps.setLong(3, sequence);
        return ps;
    }

    /** mismatches 配列に指定 type + sequence を持つ要素があるか確認。 */
    private static boolean containsMismatch(JsonNode mismatches, String type, long sequence) {
        for (JsonNode m : mismatches) {
            if (type.equals(m.get("type").asText()) && m.get("sequence").asLong() == sequence) {
                return true;
            }
        }
        return false;
    }

    /**
     * audit-service の管理 endpoint を呼び、サービス自身の検証ロジック(Sha256 再計算 + prev_hash 連鎖 + sequence 連続)が OK
     * ステータスを返すことを確認する。 既存の {@link #verifyChainIntegrity} は構造的整合性(リンク切れの有無)のみを見る。
     * 本検証はハッシュ値の再計算で改竄が無いことを実証する。
     */
    private void verifyChainViaAdminEndpoint(String tenant) throws Exception {
        ResponseEntity<String> resp =
                REST.exchange(
                        "http://localhost:"
                                + auditPort
                                + "/admin/audit-chain/verify?tenant="
                                + tenant,
                        HttpMethod.GET,
                        new HttpEntity<>(jsonHeaders()),
                        String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = OBJECT_MAPPER.readTree(resp.getBody());

        assertThat(body.get("status").asText())
                .as("tenant=%s のチェーン整合性は OK を期待", tenant)
                .isEqualTo("OK");
        assertThat(body.get("recordsScanned").asInt()).isPositive();
        assertThat(body.get("mismatches").size()).as("mismatches は空配列を期待").isZero();
    }

    /** audit_record の行を読む(sequence 昇順)。 */
    private static List<AuditRow> readAuditRecords() throws Exception {
        List<AuditRow> rows = new java.util.ArrayList<>();
        try (Connection c = POSTGRES.createConnection("");
                Statement s = c.createStatement()) {
            s.execute("SET search_path TO " + AUDIT_SCHEMA);
            try (var rs =
                    s.executeQuery(
                            "SELECT tenant_id, sequence, event_id, action, prev_hash, hash "
                                    + "FROM audit_record ORDER BY tenant_id, sequence")) {
                while (rs.next()) {
                    rows.add(
                            new AuditRow(
                                    rs.getString("tenant_id"),
                                    rs.getLong("sequence"),
                                    rs.getLong("event_id"),
                                    rs.getString("action"),
                                    rs.getString("prev_hash"),
                                    rs.getString("hash")));
                }
            }
        }
        return rows;
    }

    /**
     * 1テナント分のレコード列について、ハッシュチェーンが繋がっていることを検証する。 厳密なハッシュ値の再計算は audit-service の単体テストでカバー済みのため、
     * ここでは「リンクが切れていないこと」(prev_hash == 直前 hash)のみ確認する。
     */
    private static void verifyChainIntegrity(List<AuditRow> tenantRecords) {
        if (tenantRecords.isEmpty()) return;
        String INITIAL = "0".repeat(64);
        String prev = INITIAL;
        long expectedSeq = 1;
        for (AuditRow r : tenantRecords) {
            assertThat(r.sequence()).isEqualTo(expectedSeq++);
            assertThat(r.prevHash()).isEqualTo(prev);
            assertThat(r.hash()).matches("^[0-9a-f]{64}$");
            assertThat(r.hash()).isNotEqualTo(prev);
            prev = r.hash();
        }
    }

    record AuditRow(
            String tenantId,
            long sequence,
            long eventId,
            String action,
            String prevHash,
            String hash) {}

    private static AuditCapture pickAction(String action) {
        return AUDIT_EVENTS.stream()
                .filter(e -> action.equals(e.action()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(action + " が観測されませんでした"));
    }

    @Test
    void 未所属テナントを選択すると403() throws Exception {
        // ログイン
        ResponseEntity<String> loginResp =
                REST.exchange(
                        "http://localhost:" + identityPort + "/v1/auth/sessions",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                "{\"email\":\""
                                        + TEST_USER_EMAIL
                                        + "\",\"password\":\""
                                        + TEST_USER_PASSWORD
                                        + "\"}",
                                jsonHeaders()),
                        String.class);
        String sessionToken =
                OBJECT_MAPPER.readTree(loginResp.getBody()).get("sessionToken").asText();

        try {
            REST.exchange(
                    "http://localhost:" + identityPort + "/v1/auth/tenant-sessions",
                    HttpMethod.POST,
                    new HttpEntity<>(
                            "{\"sessionToken\":\""
                                    + sessionToken
                                    + "\",\"tenantId\":\"unknown-tenant\"}",
                            jsonHeaders()),
                    String.class);
            throw new AssertionError("403 should have been thrown");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.getResponseBodyAsString()).contains("ERR_TENANT_ACCESS_DENIED");
        }
    }

    // -----------------------------------------------------------------------------------
    // Setup helpers
    // -----------------------------------------------------------------------------------

    private static Map<String, Object> commonProps() {
        // 4 サービスを 1 JVM に同居させるため、各 service の HikariPool を絞る。
        // 各 service の application.yml 既定値 maximum-pool-size=30 だと 4×30=120 接続要求になり、
        // Postgres 既定 max_connections=100 を超えて outbox publisher の TX 取得が失敗する。
        Map<String, Object> p = new java.util.HashMap<>();
        p.put("spring.datasource.url", POSTGRES.getJdbcUrl());
        p.put("spring.datasource.username", POSTGRES.getUsername());
        p.put("spring.datasource.password", POSTGRES.getPassword());
        p.put("spring.datasource.hikari.maximum-pool-size", "5");
        p.put("spring.datasource.hikari.minimum-idle", "1");
        p.put("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers());
        p.put("logging.level.org.apache.kafka", "WARN");
        p.put("logging.level.org.springframework.kafka", "WARN");
        p.put("logging.level.org.flywaydb", "WARN");
        return p;
    }

    private static void prepareIdentitySchema() {
        // identity-broker は Pool 方式 → public スキーマに作成
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("public")
                .locations("classpath:db/migration/identity-broker")
                .load()
                .migrate();
    }

    private static void prepareInventorySchema() throws Exception {
        try (Connection c = POSTGRES.createConnection("");
                Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA IF NOT EXISTS " + INVENTORY_TENANT_SCHEMA);
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(INVENTORY_TENANT_SCHEMA)
                .locations("classpath:db/migration/inventory-core")
                .load()
                .migrate();
        // 種データ
        try (Connection c = POSTGRES.createConnection("");
                Statement s = c.createStatement()) {
            s.execute("SET search_path TO " + INVENTORY_TENANT_SCHEMA);
            s.execute(
                    """
                    INSERT INTO inventory (id, sku_id, location_id, available, reserved, version)
                    VALUES (1, 'SKU-1', 'LOC-1', 10, 0, 1)
                    """);
            // 引当ユースケースは sku_registry 投影で SKU 存在チェックを行う。
            // Master Data → master.product.v1 → SkuMasterListener の経路は
            // EndToEndMasterDataInventoryFlowIT で別途検証する。本テストは
            // master-data サービスを起動しないため擬似シードで通す。
            s.execute(
                    """
                    INSERT INTO sku_registry (code, aggregate_id, name, unit_of_measure, version)
                    VALUES ('SKU-1', 1001, 'テスト商品', 'PCS', 1)
                    """);
        }
    }

    private static void prepareAuditSchema() throws Exception {
        try (Connection c = POSTGRES.createConnection("");
                Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA IF NOT EXISTS " + AUDIT_SCHEMA);
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(AUDIT_SCHEMA)
                .locations("classpath:db/migration/audit-service")
                .load()
                .migrate();
    }

    private static void seedIdentityUser() throws Exception {
        String hash = new BCryptPasswordEncoder(10).encode(TEST_USER_PASSWORD);
        try (Connection c = POSTGRES.createConnection("");
                Statement s = c.createStatement()) {
            s.execute(
                    """
                    INSERT INTO public.users (id, email, password_hash, display_name, version)
                    VALUES (100, '%s', '%s', 'Alice', 1)
                    """
                            .formatted(TEST_USER_EMAIL, hash));
            s.execute(
                    """
                    INSERT INTO public.tenant_memberships
                        (id, user_id, tenant_id, tenant_display_name,
                         roles_json, location_scopes_json, partner_scopes_json)
                    VALUES (1000, 100, '%s', 'Dev Tenant',
                            '["INVENTORY_MANAGER"]'::jsonb,
                            '["LOC-1"]'::jsonb,
                            '[]'::jsonb)
                    """
                            .formatted(INVENTORY_TENANT));
        }
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
        return h;
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders h = jsonHeaders();
        h.setBearerAuth(token);
        return h;
    }

    // -----------------------------------------------------------------------------------
    // audit.log.v1 観測用 Kafka コンシューマ
    // -----------------------------------------------------------------------------------

    private static void startAuditConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-audit-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        auditConsumer = new KafkaConsumer<>(props);
        auditConsumer.subscribe(List.of("audit.log.v1"));

        auditConsumerThread =
                new Thread(
                        () -> {
                            try {
                                while (auditConsumerRunning) {
                                    ConsumerRecords<String, String> records =
                                            auditConsumer.poll(Duration.ofMillis(200));
                                    for (ConsumerRecord<String, String> rec : records) {
                                        try {
                                            JsonNode payload = OBJECT_MAPPER.readTree(rec.value());
                                            AUDIT_EVENTS.add(
                                                    new AuditCapture(
                                                            headerValue(rec, "tenant_id"),
                                                            optionalText(payload, "action"),
                                                            optionalText(payload, "targetType"),
                                                            optionalText(payload, "outcome"),
                                                            optionalText(
                                                                    payload, "operatorTenantId"),
                                                            optionalText(payload, "operatorUserId"),
                                                            optionalText(payload, "inputJson")));
                                        } catch (Exception ignored) {
                                            // malformed audit event はスキップ
                                        }
                                    }
                                }
                            } catch (WakeupException expected) {
                                // shutdown 経路
                            } finally {
                                try {
                                    auditConsumer.close();
                                } catch (Exception ignored) {
                                }
                            }
                        },
                        "e2e-audit-consumer");
        auditConsumerThread.setDaemon(true);
        auditConsumerThread.start();
    }

    private static void stopAuditConsumer() {
        auditConsumerRunning = false;
        if (auditConsumer != null) {
            try {
                auditConsumer.wakeup();
            } catch (Exception ignored) {
            }
        }
        if (auditConsumerThread != null) {
            try {
                auditConsumerThread.join(5000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private static String headerValue(ConsumerRecord<String, String> rec, String name) {
        Header h = rec.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
