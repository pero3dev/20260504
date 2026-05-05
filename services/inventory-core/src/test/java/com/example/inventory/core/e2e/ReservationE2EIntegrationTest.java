package com.example.inventory.core.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.inventory.core.InventoryCoreApplication;

/**
 * Reservation API のエンド・ツー・エンド統合テスト。
 *
 * <p>カバー範囲:
 *
 * <ul>
 *   <li>JWT 認証 → TenantContext 設定 → MyBatis search_path 切替
 *   <li>ユースケース → 集約 → リポジトリ → outbox 書込(InventoryReservedEvent)
 *   <li>{@code @Auditable} アスペクト → AuditEvent の outbox 書込(REQUIRES_NEW)
 *   <li>業務例外時の RFC 7807 レスポンス + 監査記録は残ること
 *   <li>未認証時の 401 ProblemDetail
 * </ul>
 *
 * <p>Kafka 発行ループは {@code platform.outbox.publisher-enabled=false} で停止し、 outbox テーブルへの書込内容のみ検証する(発行は
 * OutboxPublisherTest で別途カバー)。
 */
@SpringBootTest(
        classes = {InventoryCoreApplication.class, TestJwtDecoderConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "platform.outbox.publisher-enabled=false",
            "spring.flyway.enabled=false",
            // jwt() ポストプロセッサが SecurityContext に Authentication を直接入れるため
            // 実際の JwtDecoder は呼ばれない。autoconfig が外部の OIDC discovery を起動時に
            // フェッチするのを避けるため issuer-uri を空にし、自前のダミー JwtDecoder を提供する
            // ({@link TestSecurityConfig})。
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
            "spring.kafka.bootstrap-servers=localhost:9092",
            "platform.snowflake.worker-id=0"
        })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ReservationE2EIntegrationTest {

    private static final String TENANT_ID = "dev";
    private static final String TENANT_SCHEMA = "tenant_dev";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("inventory_core")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeAll
    static void prepareTenantSchema() throws Exception {
        // テナントスキーマを作成 → Flyway を tenant_dev に対して手動実行(本番は K8s Job で同等処理)。
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
    void seedInventory() throws Exception {
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            // search_path はテスト用に明示。本番リクエストはインターセプタが切替える。
            s.execute("SET search_path TO " + TENANT_SCHEMA);
            s.execute("TRUNCATE inventory");
            s.execute("TRUNCATE outbox");
            s.execute("TRUNCATE sku_registry");
            // 利用可能 10、引当 0、version 1 でシード。
            s.execute(
                    """
                    INSERT INTO inventory (id, sku_id, location_id, available, reserved, version)
                    VALUES (1, 'SKU-1', 'LOC-1', 10, 0, 1)
                    """);
            // Reserve ユースケースが SKU 存在チェックを行うため、Master Data 投影を擬似シードする。
            // 通常は master.product.v1 経由で SkuMasterListener が upsert する。
            s.execute(
                    """
                    INSERT INTO sku_registry (code, aggregate_id, name, unit_of_measure, version)
                    VALUES ('SKU-1', 1001, 'テスト商品', 'PCS', 1)
                    """);
        }
    }

    @Test
    void 引当成功時_在庫が更新されoutboxに在庫イベントと監査イベントが書かれる() throws Exception {
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").isNumber());

        // 在庫が available -3 / reserved +3 / version +1 になっていること
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            s.execute("SET search_path TO " + TENANT_SCHEMA);
            try (ResultSet rs =
                    s.executeQuery(
                            "SELECT available, reserved, version FROM inventory WHERE id = 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("available")).isEqualTo(7);
                assertThat(rs.getInt("reserved")).isEqualTo(3);
                assertThat(rs.getLong("version")).isEqualTo(2L);
            }
        }

        // outbox に inventory.movement.v1 と audit.log.v1 が1件ずつあること
        List<String> topics = readOutboxTopics();
        assertThat(topics).containsExactlyInAnyOrder("inventory.movement.v1", "audit.log.v1");
    }

    @Test
    void 在庫不足時_409とerrorCode付き_業務はロールバックでも監査だけは残る() throws Exception {
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
                                .content("{\"quantity\":999}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ERR_INVENTORY_INSUFFICIENT"));

        // 在庫は変わっていない
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            s.execute("SET search_path TO " + TENANT_SCHEMA);
            try (ResultSet rs =
                    s.executeQuery(
                            "SELECT available, reserved, version FROM inventory WHERE id = 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("available")).isEqualTo(10);
                assertThat(rs.getInt("reserved")).isEqualTo(0);
                assertThat(rs.getLong("version")).isEqualTo(1L);
            }
        }

        // 監査イベントだけが残る(REQUIRES_NEW で commit される)。在庫イベントは無し。
        List<String> topics = readOutboxTopics();
        assertThat(topics).containsExactly("audit.log.v1");
    }

    @Test
    void 未認証は401とerrorCode_ERR_UNAUTHENTICATED() throws Exception {
        mockMvc.perform(
                        post("/v1/inventories/{id}/reservations", 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":3}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ERR_UNAUTHENTICATED"));
    }

    @Test
    void 数量バリデーション違反は400_validation詳細を返す() throws Exception {
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
                                .content("{\"quantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"))
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    private List<String> readOutboxTopics() throws Exception {
        List<String> topics = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            s.execute("SET search_path TO " + TENANT_SCHEMA);
            try (ResultSet rs = s.executeQuery("SELECT topic FROM outbox")) {
                while (rs.next()) {
                    topics.add(rs.getString("topic"));
                }
            }
        }
        return topics;
    }
}
