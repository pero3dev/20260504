package com.example.inventory.manufacturing.architecture;

import com.example.inventory.commons.test.arch.AuditMaskingRules;
import com.example.inventory.commons.test.arch.HexagonalLayerRules;
import com.example.inventory.commons.test.arch.SecurityRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** manufacturing のアーキテクチャテスト。違反すると PR の CI を失敗させる。 ルール定義は commons-test 側で 13 サービス共通。 */
@AnalyzeClasses(
        packages = "com.example.inventory.manufacturing",
        importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainDoesNotDependOnApplicationOrAdapter = HexagonalLayerRules.layered();

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapter =
            HexagonalLayerRules.applicationDoesNotDependOnAdapter();

    @ArchTest
    static final ArchRule repositoryImplsLiveInAdapter =
            HexagonalLayerRules.repositoryImplsAreInAdapter();

    /**
     * ADR-0008 J-SOX 補完策 opt-in。 全 write 系
     * (Place/Release/Complete/HandleConsumption/HandleCompletion) は {@code @Auditable} 付与済、
     * GetWorkOrderService は read-only で対象外。
     */
    @ArchTest
    static final ArchRule writePathsAreAuditable = HexagonalLayerRules.writePathsAreAuditable();

    @ArchTest
    static final ArchRule sensitiveCommandFieldsAreMasked =
            AuditMaskingRules.sensitiveFieldsInCommandsAreMasked();

    /** A5 follow-up²³: PlatformSecurity.applyDefaults 経路を強制。 */
    @ArchTest
    static final ArchRule securityFilterChainsUsePlatformDefaults =
            SecurityRules.securityFilterChainsUsePlatformDefaults();
}
