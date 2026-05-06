package com.example.inventory.manufacturing.config;

import org.mybatis.spring.boot.autoconfigure.SqlSessionFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.inventory.commons.tenant.TenantSearchPathInterceptor;

/**
 * Bridge 方式マルチテナンシ用にテナントインターセプタを登録(ADR-0003)。 リクエスト中の MyBatis SQL 実行直前に {@code SET LOCAL
 * search_path TO tenant_<id>, public} を発行する。
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
