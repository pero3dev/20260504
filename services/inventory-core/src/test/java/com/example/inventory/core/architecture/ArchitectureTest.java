package com.example.inventory.core.architecture;

import com.example.inventory.commons.test.arch.AuditMaskingRules;
import com.example.inventory.commons.test.arch.HexagonalLayerRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** inventory-core のアーキテクチャテスト。違反すると PR の CI を失敗させる。 ルール定義そのものは commons-test に置き、13サービスで共有する。 */
@AnalyzeClasses(
        packages = "com.example.inventory.core",
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
     * ADR-0008 J-SOX 補完策 opt-in。 inventory-core の write 系 use case は全て {@code @Auditable} 付与済
     * (Reserve/Receive/Ship/Release/ApplyStockMovement/ConsumeWorkOrderComponents/ReceiveFinishedGoods
     * 等)。 Emit*Service 系は {@code DomainEventPublisher} 経由で {@code *Repository} 書込みではないため対象外、
     * RegisterSkuFromMasterService は {@code SkuRegistryPort.upsert} で同様に対象外(Javadoc に audit
     * 不要理由を明記)。
     */
    @ArchTest
    static final ArchRule writePathsAreAuditable = HexagonalLayerRules.writePathsAreAuditable();

    @ArchTest
    static final ArchRule sensitiveCommandFieldsAreMasked =
            AuditMaskingRules.sensitiveFieldsInCommandsAreMasked();
}
