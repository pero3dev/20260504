package com.example.inventory.commons.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * commons-persistence のオートコンフィグ。
 *
 * <p>{@link SnowflakeIdGenerator} をプロセス共通の Bean として提供する。 worker_id は {@code
 * platform.snowflake.worker-id} で指定(本番は StatefulSet/Deployment マニフェストが {@code POD_ORDINAL}
 * 環境変数で注入)。
 *
 * <p>個別サービスは独自の Bean を提供して上書き可能(@ConditionalOnMissingBean)。
 */
@AutoConfiguration
public class PersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SnowflakeIdGenerator snowflakeIdGenerator(
            @Value("${platform.snowflake.worker-id:0}") long workerId) {
        return new SnowflakeIdGenerator(workerId);
    }
}
