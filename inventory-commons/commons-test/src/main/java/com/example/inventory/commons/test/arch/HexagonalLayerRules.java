package com.example.inventory.commons.test.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Set;
import java.util.regex.Pattern;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

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

    /**
     * ADR-0008 J-SOX 補完策。 {@code ..application.usecase..} 配下のクラスがリポジトリ書込を呼ぶなら、 そのクラスは少なくとも 1
     * メソッド以上に {@code @Auditable} ({@value #AUDITABLE_FQN}) が付与されていなければならない。
     *
     * <p><b>クラス単位の粒度</b> を採用(public method 単位ではない)。 各 use case クラスは概ね 1〜2 公開メソッドで構成され、 全公開メソッドが 同一
     * use case ポートの実装である事が多い。 私的ヘルパに書込を分離した場合(例: ExchangeFederatedTokenService の jitProvision)、
     * 公開メソッド側に {@code @Auditable} があれば AOP 経由で監査される。
     *
     * <p><b>opt-in</b>: 各サービスの {@code ArchitectureTest} に明示的に {@code @ArchTest} を追加して有効化する。 非
     * opt-in 理由は projection / Kafka consumer 系 use case の取扱(複合: 元イベント発生源で audit 済 / 多重カウント回避 / 専用
     * {@code @AuditExempt} 設計)が未確定のため、 service 単位で順次対応する戦略。
     *
     * <p>判定基準:
     *
     * <ul>
     *   <li>「リポジトリ書込」: メソッド呼出先の宣言クラス名が {@code .*Repository} で、 メソッド名が {@code save / update / delete
     *       / insert / append / add / remove / mark.* / increment.* / persist} のいずれかに合致
     *   <li>「@Auditable 有り」: クラスのいずれかのメソッドに {@code @Auditable} 注釈が直接付与されている
     * </ul>
     */
    public static ArchRule writePathsAreAuditable() {
        return classes()
                .that()
                .resideInAPackage("..application.usecase..")
                .and(callsRepositoryWrite())
                .should(haveAtLeastOneAuditableMethod())
                // read-only サービス(write 呼出が 1 つも無い)で対象クラスが 0 件になっても vacuously 合格させる。
                .allowEmptyShould(true)
                .as("usecase クラスがリポジトリ書込を呼ぶなら、 少なくとも 1 メソッドに @Auditable を付与すること")
                .because(
                        "ADR-0008: 監査収集は AOP-only(@Auditable)。"
                                + " 付け忘れはコンプライアンス欠陥のため CI 失敗で検知する。");
    }

    private static final Pattern WRITE_METHOD_PATTERN =
            Pattern.compile(
                    "save|update|delete|insert|append|add|remove|mark[A-Z].*|increment[A-Z].*|persist");

    /** {@code @Auditable} の FQN(commons-test → commons-audit の compile 依存を増やさないため文字列参照)。 */
    private static final String AUDITABLE_FQN = "com.example.inventory.commons.audit.Auditable";

    private static DescribedPredicate<JavaClass> callsRepositoryWrite() {
        return new DescribedPredicate<>("call(s) a repository write method") {
            @Override
            public boolean test(JavaClass clazz) {
                return clazz.getMethodCallsFromSelf().stream()
                        .anyMatch(HexagonalLayerRules::isRepositoryWriteCall);
            }
        };
    }

    private static boolean isRepositoryWriteCall(JavaMethodCall call) {
        String ownerName = call.getTargetOwner().getSimpleName();
        if (!ownerName.endsWith("Repository")) {
            return false;
        }
        return WRITE_METHOD_PATTERN.matcher(call.getName()).matches();
    }

    private static ArchCondition<JavaClass> haveAtLeastOneAuditableMethod() {
        return new ArchCondition<>("have at least one method annotated with @Auditable") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                Set<JavaMethod> methods = clazz.getMethods();
                boolean hasAuditable =
                        methods.stream().anyMatch(m -> m.isAnnotatedWith(AUDITABLE_FQN));
                if (!hasAuditable) {
                    events.add(
                            SimpleConditionEvent.violated(
                                    clazz,
                                    String.format(
                                            "%s はリポジトリ書込を呼ぶが、 @Auditable メソッドが 1 つも無い"
                                                    + "(ADR-0008 audit 収集経路が欠落)",
                                            clazz.getName())));
                }
            }
        };
    }
}
