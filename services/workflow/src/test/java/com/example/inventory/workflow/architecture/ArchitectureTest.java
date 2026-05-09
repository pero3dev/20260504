package com.example.inventory.workflow.architecture;

import com.example.inventory.commons.test.arch.AuditMaskingRules;
import com.example.inventory.commons.test.arch.HexagonalLayerRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.example.inventory.workflow",
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
     * ADR-0008 J-SOX 補完策 opt-in。 Start/Advance/HandleApprovalAction は {@code @Auditable} 付与済、
     * ExpireOverdueWorkflowsService は scheduler 起動の SLA 超過 housekeeping のため {@code @AuditExempt}。
     * Get は read-only で対象外。
     */
    @ArchTest
    static final ArchRule writePathsAreAuditable = HexagonalLayerRules.writePathsAreAuditable();

    @ArchTest
    static final ArchRule sensitiveCommandFieldsAreMasked =
            AuditMaskingRules.sensitiveFieldsInCommandsAreMasked();
}
