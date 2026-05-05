package com.example.inventory.core.config;

import org.mybatis.spring.boot.autoconfigure.SqlSessionFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.inventory.commons.tenant.TenantSearchPathInterceptor;

/**
 * 本サービスの MyBatis SqlSessionFactory に対して、commons-tenant の テナント {@code search_path}
 * インターセプタを登録する(ADR-0003)。
 */
@Configuration
public class MyBatisConfig {

    @Bean
    public TenantSearchPathInterceptor tenantSearchPathInterceptor() {
        return new TenantSearchPathInterceptor();
    }

    @Bean
    public SqlSessionFactoryBeanCustomizer registerTenantInterceptor(
            TenantSearchPathInterceptor interceptor) {
        return factoryBean -> factoryBean.setPlugins(interceptor);
    }
}
