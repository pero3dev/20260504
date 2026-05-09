package com.example.inventory.identity.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.inventory.identity.IdentityBrokerApplication;

/**
 * ADR-0023 即時 token revocation の単一 context E2E。 Postgres + Redis を Testcontainers で立ち上げて、 「revoke
 * 登録 → 後続 request が 401 になる」 を実 fixture で検証する。
 *
 * <p>これまで {@code UserManagementServiceTest} は Mockito で revocationStore.revokeUser の引数だけ verify
 * していたが、 RevocationCheckFilter (commons-security) → RedisRevocationStore → Spring Security 401 RFC
 * 7807 の通し path を 1 度も実機検証していなかった。 本 IT が初の通し検証。
 *
 * <p>scope:
 *
 * <ul>
 *   <li>controller(POST /v1/admin/users/{id}/revoke-tokens) → service → Redis 書込
 *   <li>後続 request(任意の {@code /v1/admin/**})が RevocationCheckFilter で 401 にされる
 *   <li>revocation 無し user の request は controller まで到達(filter は素通し)
 * </ul>
 *
 * <p>Docker 不在環境(Windows + 古い Docker Desktop 等)では {@link Testcontainers#disabledWithoutDocker} で
 * auto-skip。 CI(ubuntu-latest)では実機実行。
 */
@SpringBootTest(
        classes = IdentityBrokerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.enabled=false",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
            "platform.outbox.publisher-enabled=false",
            "platform.snowflake.worker-id=0"
        })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RevocationE2EIntegrationTest {

    private static final long ADMIN_USER_ID = 999L;
    private static final long VICTIM_USER_ID = 900100L;
    private static final long INNOCENT_USER_ID = 800200L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("identity_broker")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void wireProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
    }

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/identity-broker")
                .load()
                .migrate();
    }

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void seed() throws Exception {
        // tenant_memberships → users の順に削除(FK のため)。 outbox も clean。
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            s.execute("TRUNCATE tenant_memberships, users, outbox RESTART IDENTITY CASCADE");
            s.execute(
                    "INSERT INTO users (id, email, password_hash, display_name, status, version) "
                            + "VALUES "
                            + "("
                            + ADMIN_USER_ID
                            + ", 'admin@example.com', '$external_federation$', 'Admin',"
                            + " 'ACTIVE', 0),"
                            + "("
                            + VICTIM_USER_ID
                            + ", 'victim@example.com', '$external_federation$', 'Victim',"
                            + " 'ACTIVE', 0),"
                            + "("
                            + INNOCENT_USER_ID
                            + ", 'innocent@example.com', '$external_federation$', 'Innocent',"
                            + " 'ACTIVE', 0)");
        }
        // 前テストの revocation キーが残っていると干渉するため明示的に削除
        redis.delete("revocation:user:" + VICTIM_USER_ID);
        redis.delete("revocation:user:" + INNOCENT_USER_ID);
        redis.delete("revocation:user:" + ADMIN_USER_ID);
    }

    @Test
    void revoke_tokens_API_は_Redis_に_revocation_キーを書き込む() throws Exception {
        mockMvc.perform(
                        post("/v1/admin/users/{id}/revoke-tokens", VICTIM_USER_ID)
                                .with(adminJwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"suspected token leak (IT)\"}"))
                .andExpect(status().isNoContent());

        String key = "revocation:user:" + VICTIM_USER_ID;
        assertThat(redis.hasKey(key)).as("Redis key " + key).isTrue();
        Long ttl = redis.getExpire(key);
        assertThat(ttl).as("revocation TTL").isPositive().isLessThanOrEqualTo(15 * 60);
    }

    @Test
    void revocation_キー有り_の_userId_の_request_は_RevocationCheckFilter_で_401() throws Exception {
        // 直接 Redis に 15min TTL で revocation を書き込む(ADR-0023 の Redis レイアウト直叩き)
        redis.opsForValue().set("revocation:user:" + VICTIM_USER_ID, "1", Duration.ofMinutes(15));

        // SUPER_ADMIN role 付きでも sub=victim なら filter が先に 401 を返す
        mockMvc.perform(
                        get("/v1/admin/users/{id}", VICTIM_USER_ID)
                                .with(jwtForUser(VICTIM_USER_ID)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revocation_無し_user_は_request_が_controller_に到達_して_200() throws Exception {
        mockMvc.perform(
                        get("/v1/admin/users/{id}", INNOCENT_USER_ID)
                                .with(jwtForUser(INNOCENT_USER_ID)))
                .andExpect(status().isOk());
    }

    @Test
    void revoke_tokens_API_経由で_revoke_された_user_の_次_request_は_401() throws Exception {
        // admin が victim を revoke
        mockMvc.perform(
                        post("/v1/admin/users/{id}/revoke-tokens", VICTIM_USER_ID)
                                .with(adminJwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"end-to-end IT path\"}"))
                .andExpect(status().isNoContent());

        // 同 user の access token は次 request で 401 になる
        mockMvc.perform(
                        get("/v1/admin/users/{id}", VICTIM_USER_ID)
                                .with(jwtForUser(VICTIM_USER_ID)))
                .andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwtForUser(ADMIN_USER_ID);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtForUser(
            long userId) {
        return jwt().jwt(b -> b.subject(Long.toString(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
    }
}
