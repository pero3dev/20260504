package com.example.inventory.commons.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * commons-security auto-config の RevocationStore 解決ロジックをガードする。
 *
 * <p>過去のリグレッション 2 件を防ぐ:
 *
 * <ul>
 *   <li>(¹⁷-hotfix) 外側 auto-config の method 引数に {@code StringRedisTemplate} を書いていたため、 Redis 不在
 *       service が context 起動で {@link NoClassDefFoundError} を発生
 *   <li>(²⁰-hotfix) Redis 有り service でも nested {@code @Configuration} と外側
 *       {@code @ConditionalOnMissingBean(RevocationStore.class)} の評価順序で NoOp が先に登録され、
 *       RevocationCheckFilter が Redis を読まずに常に false を返していた(IT で初判明)
 * </ul>
 */
class PlatformSecurityAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    JacksonAutoConfiguration.class,
                                    RedisRevocationStoreAutoConfiguration.class,
                                    NoOpRevocationStoreAutoConfiguration.class,
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

    @Test
    void Redis_classpath_有り_かつ_RedisConnectionFactory_Bean_有り_なら_RedisRevocationStore_を採る() {
        // RedisAutoConfiguration が LettuceConnectionFactory を提供すれば、 NoOp ではなく Redis 変種が選ばれる。
        // ²⁰-hotfix で auto-config を 3 ファイルに分割し @AutoConfiguration(after=...) で順序付けした
        // 効果がここで効く。
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                JacksonAutoConfiguration.class,
                                RedisAutoConfiguration.class,
                                RedisRevocationStoreAutoConfiguration.class,
                                NoOpRevocationStoreAutoConfiguration.class,
                                PlatformSecurityAutoConfiguration.class))
                .run(
                        ctx -> {
                            assertThat(ctx).hasNotFailed();
                            assertThat(ctx).hasSingleBean(RevocationStore.class);
                            assertThat(ctx.getBean(RevocationStore.class))
                                    .isInstanceOf(RedisRevocationStore.class);
                            assertThat(ctx).hasSingleBean(RevocationCheckFilter.class);
                        });
    }
}
