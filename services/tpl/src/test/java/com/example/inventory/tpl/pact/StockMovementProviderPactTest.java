package com.example.inventory.tpl.pact;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;

/**
 * Provider 側 Pact verifier — Folder source 経路(ADR-0019 Phase 2.2)。 {@code tpl.stock.movement.v1} を
 * verify。 Broker 経路は {@link TplProviderBrokerPactTest}。
 */
@Provider("tpl")
@PactFolder("../inventory-core/target/pacts")
@EnabledIfSystemProperty(named = "pact.providerVerifier.enabled", matches = "true")
public class StockMovementProviderPactTest extends TplProviderPactBase {}
