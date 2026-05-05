package com.example.inventory.commons.test.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;

/**
 * 全サービス共通のヘキサゴナル層ルール。各サービスは下記のように import して使用する:
 *
 * <pre>{@code
 * @AnalyzeClasses(packages = "com.example.inventory.core")
 * class ArchitectureTest {
 *     @ArchTest static final ArchRule layers = HexagonalLayerRules.layered();
 *     @ArchTest static final ArchRule auditable = HexagonalLayerRules.writePathsAreAuditable();
 * }
 * }</pre>
 *
 * <p>層構造(ADR-0001、ADR-0009): {@code domain → application → adapter} の単方向依存。
 *
 * <p>{@code writePathsAreAuditable} は ADR-0008 の補完策を強制する。 リポジトリの {@code save}/{@code delete}
 * を呼び出すアプリケーション層メソッドは 直接または間接的に {@code @Auditable} 配下にあること。
 */
public final class HexagonalLayerRules {

    private HexagonalLayerRules() {}

    /** ドメイン層は application/adapter 層に依存してはならない。 */
    public static ArchRule layered() {
        return noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..application..", "..adapter..");
    }

    /** アプリケーション層は adapter 層に依存してはならない(ポート経由のみ)。 */
    public static ArchRule applicationDoesNotDependOnAdapter() {
        return noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..adapter..");
    }

    /** RepositoryImpl は adapter/out/persistence 配下に置くこと。 */
    public static ArchRule repositoryImplsAreInAdapter() {
        return classes()
                .that()
                .haveSimpleNameEndingWith("RepositoryImpl")
                .should()
                .resideInAPackage("..adapter.out.persistence..");
    }
}
