package com.example.inventory.retail.pact;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;

/**
 * Provider 側 Pact verifier — Folder source 経路(ADR-0019 Phase 2.2)。 {@code retail.order.placed.v1} を
 * verify。 Broker 経路は {@link RetailProviderBrokerPactTest}。
 */
@Provider("retail-ec")
@PactFolder("../inventory-core/target/pacts")
@EnabledIfSystemProperty(named = "pact.providerVerifier.enabled", matches = "true")
public class RetailOrderPlacedProviderPactTest extends RetailProviderPactBase {}
