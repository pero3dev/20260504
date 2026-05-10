package com.example.inventory.hub.architecture;

import com.example.inventory.commons.test.arch.HexagonalLayerRules;
import com.example.inventory.commons.test.arch.SecurityRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.example.inventory.hub",
        importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapter =
            HexagonalLayerRules.applicationDoesNotDependOnAdapter();

    /**
     * ADR-0008 J-SOX 補完策 opt-in。 integration-hub は MVP 時点で {@code application/usecase} 配下に use case
     * クラスが無い (CSV 1 アダプタの adapter 直 wiring) ため、 rule は対象 0 件で vacuously 合格。 use case 追加時にこのテストが
     * 自動で強制を効かせる。
     */
    @ArchTest
    static final ArchRule writePathsAreAuditable = HexagonalLayerRules.writePathsAreAuditable();

    /** A5 follow-up²³: PlatformSecurity.applyDefaults 経路を強制。 */
    @ArchTest
    static final ArchRule securityFilterChainsUsePlatformDefaults =
            SecurityRules.securityFilterChainsUsePlatformDefaults();
}
