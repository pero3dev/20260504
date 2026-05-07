package com.example.inventory.wholesale.pact;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;

/**
 * Provider 側 Pact verifier — Folder source 経路(ADR-0019 Phase 2)。
 *
 * <p>{@code services/inventory-core/target/pacts/} の pact ファイルを直接読んで verify する。 reactor 内 mvn
 * verify(ci.yml) で走る経路で、 Broker を必要としない。 Broker 経由 verify は {@link WholesaleProviderBrokerPactTest}
 * 参照。
 */
@Provider("wholesale")
@PactFolder("../inventory-core/target/pacts")
@EnabledIfSystemProperty(named = "pact.providerVerifier.enabled", matches = "true")
public class WholesaleProviderPactTest extends WholesaleProviderPactBase {}
