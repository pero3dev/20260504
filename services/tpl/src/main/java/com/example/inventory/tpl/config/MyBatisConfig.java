package com.example.inventory.tpl.config;

import org.mybatis.spring.boot.autoconfigure.SqlSessionFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.inventory.commons.tenant.TenantSearchPathInterceptor;

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
