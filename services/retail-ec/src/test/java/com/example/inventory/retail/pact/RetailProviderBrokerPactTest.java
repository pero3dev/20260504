package com.example.inventory.retail.pact;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;

/**
 * Provider 側 Pact verifier — Broker source 経路(ADR-0019 Phase 3.5)。 {@code PACT_BROKER_URL} 設定時のみ有効。
 * 詳細は {@code services/wholesale/.../WholesaleProviderBrokerPactTest} 参照。
 */
@Provider("retail-ec")
@PactBroker
@EnabledIfEnvironmentVariable(named = "PACT_BROKER_URL", matches = "https?://.+")
public class RetailProviderBrokerPactTest extends RetailProviderPactBase {}
