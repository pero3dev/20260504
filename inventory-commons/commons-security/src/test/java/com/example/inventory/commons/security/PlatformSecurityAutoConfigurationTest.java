package com.example.inventory.commons.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * {@link PlatformSecurityAutoConfiguration} が spring-data-redis 不在の classpath でも{@link
 * NoClassDefFoundError} を起こさず、 {@link NoOpRevocationStore} に fallback することを保証する。
 *
 * <p>過去 ADR-0023 実装初版は外側 auto-config クラスの method 引数に {@code StringRedisTemplate} を直接書いており、 Redis
 * 非依存サービス (inventory-core 等) で {@link org.springframework.context.ApplicationContext} 起動時に {@code
 * Failed to introspect Class[...PlatformSecurityAutoConfiguration]} を発生させていた。 nested
 * {@code @Configuration} + class-level {@link
 * org.springframework.boot.autoconfigure.condition.ConditionalOnClass} で 隔離する形に修正したため、
 * そのリグレッションを防ぐ。
 */
class PlatformSecurityAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    JacksonAutoConfiguration.class,
                                    PlatformSecurityAutoConfiguration.class));

    @Test
    void Redis_不在_classpath_でも起動でき_NoOpRevocationStore_に_fallback() {
        runner.withClassLoader(
                        new FilteredClassLoader(
                                "org.springframework.data.redis.core.StringRedisTemplate",
                                "org.springframework.data.redis.connection.RedisConnectionFactory"))
                .run(
                        ctx -> {
                            assertThat(ctx).hasNotFailed();
                            assertThat(ctx).hasSingleBean(RevocationStore.class);
                            assertThat(ctx.getBean(RevocationStore.class))
                                    .isInstanceOf(NoOpRevocationStore.class);
                            assertThat(ctx).hasSingleBean(RevocationCheckFilter.class);
                        });
    }

    @Test
    void Redis_classpath_有り_だが_RedisConnectionFactory_Bean_無し_なら_NoOp_を採る() {
        runner.run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(RevocationStore.class);
                    assertThat(ctx.getBean(RevocationStore.class))
                            .isInstanceOf(NoOpRevocationStore.class);
                });
    }
}
