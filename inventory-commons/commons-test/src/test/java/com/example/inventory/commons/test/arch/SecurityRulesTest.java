package com.example.inventory.commons.test.arch;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.inventory.commons.test.arch.fixtures.config.BadSecurityConfig;
import com.example.inventory.commons.test.arch.fixtures.config.ExemptSecurityConfig;
import com.example.inventory.commons.test.arch.fixtures.config.GoodSecurityConfig;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

/** {@link SecurityRules#securityFilterChainsUsePlatformDefaults()} の動作検証。 */
class SecurityRulesTest {

    @Test
    void applyDefaults_を呼ぶ_SecurityFilterChain_Bean_は合格する() {
        JavaClasses classes = new ClassFileImporter().importClasses(GoodSecurityConfig.class);

        assertThatCode(() -> SecurityRules.securityFilterChainsUsePlatformDefaults().check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void applyDefaults_を呼ばず_exempt_も無い_SecurityFilterChain_Bean_は違反として検出される() {
        JavaClasses classes = new ClassFileImporter().importClasses(BadSecurityConfig.class);

        assertThatThrownBy(
                        () ->
                                SecurityRules.securityFilterChainsUsePlatformDefaults()
                                        .check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("BadSecurityConfig")
                .hasMessageContaining("applyDefaults");
    }

    @Test
    void SecurityFilterChainExempt_付き_は_applyDefaults_を呼ばなくても合格する() {
        JavaClasses classes = new ClassFileImporter().importClasses(ExemptSecurityConfig.class);

        assertThatCode(() -> SecurityRules.securityFilterChainsUsePlatformDefaults().check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void SecurityFilterChain_Bean_を持たない_class_は_vacuously_合格() {
        // SecurityRulesTest 自身を import classes に渡す。 Bean SecurityFilterChain が無いので allowEmptyShould
        // で合格。
        JavaClasses classes = new ClassFileImporter().importClasses(SecurityRulesTest.class);

        assertThatCode(() -> SecurityRules.securityFilterChainsUsePlatformDefaults().check(classes))
                .doesNotThrowAnyException();
    }
}
