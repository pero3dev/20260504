package com.example.inventory.audit.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Merkle anchor 設定の有効化(ADR-0008、D3)。 */
@Configuration
@EnableConfigurationProperties(AnchorProperties.class)
public class AnchorConfiguration {}
