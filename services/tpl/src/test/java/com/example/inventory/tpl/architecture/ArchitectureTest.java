package com.example.inventory.tpl.architecture;

import com.example.inventory.commons.test.arch.AuditMaskingRules;
import com.example.inventory.commons.test.arch.HexagonalLayerRules;
import com.example.inventory.commons.test.arch.SecurityRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.example.inventory.tpl",
        importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest static final ArchRule layered = HexagonalLayerRules.layered();

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapter =
            HexagonalLayerRules.applicationDoesNotDependOnAdapter();

    @ArchTest
    static final ArchRule reposInAdapter = HexagonalLayerRules.repositoryImplsAreInAdapter();

    /**
     * ADR-0008 J-SOX 補完策 opt-in。 PlanStockMovementService は {@code @Auditable} 付与済、
     * GetStockMovementService は read-only で対象外。
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
