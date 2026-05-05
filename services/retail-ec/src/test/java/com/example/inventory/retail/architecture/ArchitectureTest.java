package com.example.inventory.retail.architecture;

import com.example.inventory.commons.test.arch.AuditMaskingRules;
import com.example.inventory.commons.test.arch.HexagonalLayerRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.example.inventory.retail",
        importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest static final ArchRule layered = HexagonalLayerRules.layered();

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapter =
            HexagonalLayerRules.applicationDoesNotDependOnAdapter();

    @ArchTest
    static final ArchRule reposInAdapter = HexagonalLayerRules.repositoryImplsAreInAdapter();

    @ArchTest
    static final ArchRule sensitiveCommandFieldsAreMasked =
            AuditMaskingRules.sensitiveFieldsInCommandsAreMasked();
}
