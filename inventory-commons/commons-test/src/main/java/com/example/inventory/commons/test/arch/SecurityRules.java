package com.example.inventory.commons.test.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * commons-security の {@code PlatformSecurity.applyDefaults} 経路を 13 services で漏れなく踏ませるための ArchUnit
 * ルール集。
 *
 * <p>背景: ADR-0023 即時 token revocation の {@code RevocationCheckFilter} や、 共通の RFC 7807 認証/認可エラー、
 * {@code TenantContextFilter} は {@code PlatformSecurity.applyDefaults} 経由でしか chain に入らない。 個々の
 * service が自前で {@code HttpSecurity.csrf(..).oauth2ResourceServer(..)} を組み立て始めると、 共通機能の漏れに気付かないまま
 * production に出る危険がある(ADR-0023 / 監査要件 が黙って外れる)。
 *
 * <p>本ルールは「{@code @Bean} かつ戻り値が {@code SecurityFilterChain} のメソッド」 を全 service から検出し、 {@code
 * PlatformSecurity.applyDefaults} を呼ばない場合は {@code @SecurityFilterChainExempt(reason="...")} で明示的に
 * 例外宣言することを CI 強制する。 ¹⁹-²² で完成させた即時 revocation 経路を 将来追加されるサービスで「気付かず外す」 事故を防ぐ。
 *
 * <p>opt-in: 各サービスの {@code ArchitectureTest} で個別に有効化する。 {@code commons-security} に依存しないサービスでは適用しない
 * (commons-security 非依存 = 認証無し純 internal job 系を想定、 現状 13/13 service は依存しているので全 opt-in 推奨)。
 */
public final class SecurityRules {

    private SecurityRules() {}

    /**
     * Spring Boot の {@code @Bean SecurityFilterChain} のうち、 {@code PlatformSecurity.applyDefaults} を
     * 呼ばないものを検出。 例外は {@code @SecurityFilterChainExempt(reason="...")} で明示宣言。
     */
    public static ArchRule securityFilterChainsUsePlatformDefaults() {
        return methods()
                .that()
                .areAnnotatedWith(BEAN_FQN)
                .and()
                .haveRawReturnType(SECURITY_FILTER_CHAIN_FQN)
                .should(callPlatformSecurityApplyDefaultsOrBeExempt())
                // service が SecurityFilterChain Bean を 1 つも持たない場合(理論上ありうる)に vacuously 合格させる。
                .allowEmptyShould(true)
                .as(
                        "@Bean SecurityFilterChain は PlatformSecurity.applyDefaults を経由するか、 "
                                + "@SecurityFilterChainExempt で例外宣言すること")
                .because(
                        "ADR-0023 即時 revocation / RFC 7807 / TenantContextFilter は applyDefaults 経由でのみ chain"
                                + " に乗る。 自前 HttpSecurity 構成は黙って共通機能を外す事故を起こすため CI 強制。");
    }

    /** {@code @Bean} の FQN(commons-test → spring-context の compile 依存を増やさないため文字列参照)。 */
    private static final String BEAN_FQN = "org.springframework.context.annotation.Bean";

    /**
     * {@code SecurityFilterChain} の FQN(commons-test → spring-security の compile 依存を増やさないため文字列参照)。
     */
    private static final String SECURITY_FILTER_CHAIN_FQN =
            "org.springframework.security.web.SecurityFilterChain";

    /**
     * {@code @SecurityFilterChainExempt} の FQN(commons-test → commons-security の compile 依存を増やさないため
     * 文字列参照、 {@link
     * com.example.inventory.commons.test.arch.HexagonalLayerRules#writePathsAreAuditable} と同じ
     * pattern)。
     */
    private static final String EXEMPT_ANNOTATION_FQN =
            "com.example.inventory.commons.security.SecurityFilterChainExempt";

    /** {@code PlatformSecurity} の FQN(applyDefaults 呼出のターゲット owner 判定用)。 */
    private static final String PLATFORM_SECURITY_FQN =
            "com.example.inventory.commons.security.PlatformSecurity";

    /** {@code applyDefaults} メソッド名。 PlatformSecurity の signature 変更時はここも追従。 */
    private static final String APPLY_DEFAULTS_METHOD = "applyDefaults";

    private static ArchCondition<JavaMethod> callPlatformSecurityApplyDefaultsOrBeExempt() {
        return new ArchCondition<>(
                "call PlatformSecurity.applyDefaults() or be @SecurityFilterChainExempt") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (method.isAnnotatedWith(EXEMPT_ANNOTATION_FQN)) {
                    return;
                }
                boolean callsApplyDefaults =
                        method.getMethodCallsFromSelf().stream()
                                .anyMatch(SecurityRules::isPlatformSecurityApplyDefaultsCall);
                if (!callsApplyDefaults) {
                    events.add(
                            SimpleConditionEvent.violated(
                                    method,
                                    String.format(
                                            "%s.%s() は SecurityFilterChain Bean だが"
                                                    + " PlatformSecurity.applyDefaults を呼ばない。"
                                                    + " 共通 Bearer / Revocation / Tenant filter が抜ける。"
                                                    + " 正当な例外なら @SecurityFilterChainExempt(reason=...) を付与する。",
                                            method.getOwner().getName(), method.getName())));
                }
            }
        };
    }

    private static boolean isPlatformSecurityApplyDefaultsCall(JavaMethodCall call) {
        return PLATFORM_SECURITY_FQN.equals(call.getTargetOwner().getFullName())
                && APPLY_DEFAULTS_METHOD.equals(call.getName());
    }
}
