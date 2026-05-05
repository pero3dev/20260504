package com.example.inventory.readmodel.architecture;

import com.example.inventory.commons.test.arch.AuditMaskingRules;
import com.example.inventory.commons.test.arch.HexagonalLayerRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** inventory-read-model のアーキテクチャテスト。commons-test の標準ルールを継承する。 */
@AnalyzeClasses(
        packages = "com.example.inventory.readmodel",
        importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainDoesNotDependOnApplicationOrAdapter = HexagonalLayerRules.layered();

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapter =
            HexagonalLayerRules.applicationDoesNotDependOnAdapter();

    @ArchTest
    static final ArchRule sensitiveCommandFieldsAreMasked =
            AuditMaskingRules.sensitiveFieldsInCommandsAreMasked();
}
