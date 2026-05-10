package com.example.inventory.identity.architecture;

import com.example.inventory.commons.test.arch.AuditMaskingRules;
import com.example.inventory.commons.test.arch.HexagonalLayerRules;
import com.example.inventory.commons.test.arch.SecurityRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.example.inventory.identity",
        importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest static final ArchRule layered = HexagonalLayerRules.layered();

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapter =
            HexagonalLayerRules.applicationDoesNotDependOnAdapter();

    @ArchTest
    static final ArchRule reposInAdapter = HexagonalLayerRules.repositoryImplsAreInAdapter();

    /**
     * ADR-0008 J-SOX 補完策:application.usecase クラスがリポジトリ書込を呼ぶなら 少なくとも 1 メソッドに {@code @Auditable}
     * を付与すること。 identity-broker は A5 follow-up² / ³ 完了で全 usecase が compliant のため pilot opt-in。 他 12
     * サービスは projection / Kafka consumer 系の取扱(@AuditExempt の設計)が決まり次第順次 opt-in。
     */
    @ArchTest
    static final ArchRule writePathsAreAuditable = HexagonalLayerRules.writePathsAreAuditable();

    /**
     * AuthenticateUseCase.Command の password と SelectTenantUseCase.Command の sessionToken
     * に @AuditMask が付いていることを CI で機械的に検証する(audit ログへの平文漏洩防止)。
     */
    @ArchTest
    static final ArchRule sensitiveCommandFieldsAreMasked =
            AuditMaskingRules.sensitiveFieldsInCommandsAreMasked();

    /**
     * A5 follow-up²³: 全 service で {@code PlatformSecurity.applyDefaults} 経路を強制し、 ADR-0023
     * RevocationCheckFilter / RFC 7807 / TenantContextFilter が無音で外れる事故を防ぐ。 publicFilterChain は
     * {@code @SecurityFilterChainExempt(reason="...")} で明示宣言済。
     */
    @ArchTest
    static final ArchRule securityFilterChainsUsePlatformDefaults =
            SecurityRules.securityFilterChainsUsePlatformDefaults();
}
