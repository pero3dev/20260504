package com.example.inventory.wholesale.pact;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;

/**
 * Provider 側 Pact verifier — Broker source 経路(ADR-0019 Phase 3.5)。
 *
 * <p>{@code PACT_BROKER_URL} env 設定時のみ実行。 contract は Broker から取得し、 verify 結果は {@code
 * pact.verifier.publishResults=true} 時に Broker へ publish back する。 これで can-i-deploy が「Provider
 * verify が緑」を判定できる。
 *
 * <p>system properties:
 *
 * <ul>
 *   <li>{@code pactbroker.url} = http://localhost:9292
 *   <li>{@code pactbroker.auth.username} / {@code pactbroker.auth.password}
 *   <li>{@code pact.provider.version} = git SHA など
 *   <li>{@code pact.provider.tag} = main, pr-NNN 等(任意)
 *   <li>{@code pact.verifier.publishResults} = true
 * </ul>
 */
@Provider("wholesale")
@PactBroker
@EnabledIfEnvironmentVariable(named = "PACT_BROKER_URL", matches = "https?://.+")
public class WholesaleProviderBrokerPactTest extends WholesaleProviderPactBase {}
