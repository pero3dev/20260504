package com.example.inventory.readmodel.architecture;

import com.example.inventory.commons.test.arch.AuditMaskingRules;
import com.example.inventory.commons.test.arch.HexagonalLayerRules;
import com.example.inventory.commons.test.arch.SecurityRules;
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

    /**
     * ADR-0008 J-SOX 補完策 opt-in。 inventory-read-model は projection store ({@code
     * InventoryProjectionStore})を使用し {@code *Repository} 経路では書込みしないため、 rule は vacuously 合格
     * (`allowEmptyShould(true)` で対象 0 件でも pass)。 GetInventoryService は
     * {@code @Auditable(read=true)} で参照行為を audit 記録、 ApplyInventoryMovementService は projection
     * 自身のため audit 不要(元イベントは inventory-core 側で audit 済)。
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
