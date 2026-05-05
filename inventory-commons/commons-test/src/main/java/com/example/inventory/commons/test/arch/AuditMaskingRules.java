package com.example.inventory.commons.test.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import com.tngtech.archunit.lang.ArchRule;

/**
 * 機微情報を audit ログから守るための ArchUnit ルール。
 *
 * <p>ユースケースのコマンド({@code *Command} 型)で機微情報を示唆する名前のフィールドは、 commons-audit の {@code @AuditMask}
 * を必須にする。これにより、`@Auditable` メソッドの 第 1 引数として serialize されたとき、生値が audit ストアに残らないことを CI で機械的に保証する。
 *
 * <p>ルール本体は {@code @AuditMask} の FQN 文字列を参照するため、commons-test は commons-audit を実行時依存として持たない(テスト時のみ
 * test scope で利用)。
 */
public final class AuditMaskingRules {

    /** 機微情報を示唆するフィールド名(部分一致 / 大文字小文字区別なし)。 */
    private static final String SENSITIVE_NAME_PATTERN =
            "(?i).*(password|passwd|pwd|secret|token|apikey|api_key|credential|"
                    + "privatekey|private_key|ssn|creditcard|credit_card).*";

    private static final String AUDIT_MASK_FQN = "com.example.inventory.commons.audit.AuditMask";

    private AuditMaskingRules() {}

    /**
     * Command 型(クラス名末尾 {@code Command})で機微情報名のフィールドは {@code @AuditMask} を持つこと。
     *
     * <p>違反例:
     *
     * <pre>{@code
     * record AuthenticateCommand(String email, String password) { }   // password に @AuditMask 無し → 違反
     * }</pre>
     *
     * <p>合格例:
     *
     * <pre>{@code
     * record AuthenticateCommand(String email, @AuditMask String password) { }
     * }</pre>
     */
    public static ArchRule sensitiveFieldsInCommandsAreMasked() {
        return fields().that()
                .areDeclaredInClassesThat()
                .haveSimpleNameEndingWith("Command")
                .and()
                .haveNameMatching(SENSITIVE_NAME_PATTERN)
                .should()
                .beAnnotatedWith(AUDIT_MASK_FQN)
                // 機微情報フィールドが 1 件も存在しないモジュール(Inventory Core 等)で
                // ArchUnit の「空チェック失敗」既定挙動が誤検出になるため明示的に許可。
                // 「フィールドが無い」=「違反が無い」=「合格」が本ルールの妥当な解釈。
                .allowEmptyShould(true)
                .as("Command 型の機微情報フィールドは @AuditMask を必須(audit ログへの平文漏洩防止)");
    }

    /**
     * Request 型(クラス名末尾 {@code Request})で機微情報名のフィールドも {@code @AuditMask} を持つこと。
     *
     * <p>OpenAPI 生成 DTO はコントローラで Command に詰め替えられて @Auditable に渡るため、 Request
     * 段階で機微情報マスクを強制する必要は技術的にはないが、DTO 自体が SLF4J / Datadog ログ等に出力された場合の漏洩防止として有効。
     */
    public static ArchRule sensitiveFieldsInRequestsAreMasked() {
        return fields().that()
                .areDeclaredInClassesThat()
                .haveSimpleNameEndingWith("Request")
                .and()
                .haveNameMatching(SENSITIVE_NAME_PATTERN)
                .should()
                .beAnnotatedWith(AUDIT_MASK_FQN)
                .allowEmptyShould(true)
                .as("Request 型の機微情報フィールドも @AuditMask を推奨");
    }
}
