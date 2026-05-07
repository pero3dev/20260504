package com.example.inventory.manufacturing.pact;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;

/**
 * Provider 側 Pact verifier — Folder source 経路(ADR-0019 Phase 2.2)。 {@code
 * manufacturing.work_order.released.v1} を verify。 Broker 経路は {@link
 * ManufacturingProviderBrokerPactTest}。
 */
@Provider("manufacturing")
@PactFolder("../inventory-core/target/pacts")
@EnabledIfSystemProperty(named = "pact.providerVerifier.enabled", matches = "true")
public class WorkOrderReleasedProviderPactTest extends ManufacturingProviderPactBase {}
