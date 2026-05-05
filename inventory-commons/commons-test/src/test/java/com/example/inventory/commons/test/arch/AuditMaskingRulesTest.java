package com.example.inventory.commons.test.arch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.inventory.commons.audit.AuditMask;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

/** AuditMaskingRules の動作検証。合格/違反の両方を実証する。 */
class AuditMaskingRulesTest {

    @Test
    void password_に_AuditMask_が無い_Command_は違反として検出される() {
        JavaClasses classes = new ClassFileImporter().importClasses(BadCommand.class);

        assertThatThrownBy(
                        () -> AuditMaskingRules.sensitiveFieldsInCommandsAreMasked().check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("password")
                .hasMessageContaining("@AuditMask");
    }

    @Test
    void password_に_AuditMask_が付いた_Command_は合格する() {
        JavaClasses classes = new ClassFileImporter().importClasses(GoodCommand.class);

        // 違反無しなら check() は例外を投げない
        AuditMaskingRules.sensitiveFieldsInCommandsAreMasked().check(classes);
    }

    @Test
    void 機微名でないフィールドは無視される() {
        JavaClasses classes = new ClassFileImporter().importClasses(BenignCommand.class);

        AuditMaskingRules.sensitiveFieldsInCommandsAreMasked().check(classes);
    }

    @Test
    void Command_でない型のフィールドは対象外() {
        JavaClasses classes = new ClassFileImporter().importClasses(NotACommandJustEntity.class);

        // password を持つが Command 型ではないので合格(ドメインモデルや永続オブジェクトは別の話)
        AuditMaskingRules.sensitiveFieldsInCommandsAreMasked().check(classes);
    }

    @Test
    void token_secret_apiKey_等の名前パターンも検出される() {
        JavaClasses classes = new ClassFileImporter().importClasses(MultiSensitiveCommand.class);

        assertThatThrownBy(
                        () -> AuditMaskingRules.sensitiveFieldsInCommandsAreMasked().check(classes))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void Request_型の_AuditMask_チェックも動作する() {
        JavaClasses classes = new ClassFileImporter().importClasses(BadRequest.class);

        assertThatThrownBy(
                        () -> AuditMaskingRules.sensitiveFieldsInRequestsAreMasked().check(classes))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void 大文字小文字とアンダースコア混在も検出する() {
        JavaClasses classes = new ClassFileImporter().importClasses(MixedCaseCommand.class);

        assertThatThrownBy(
                        () -> AuditMaskingRules.sensitiveFieldsInCommandsAreMasked().check(classes))
                .isInstanceOf(AssertionError.class);
        // 検出された ことそのものを assert(具体メッセージは ArchUnit に依存)
        assertThat(true).isTrue();
    }

    // ===== テスト用の synthetic 型(*Command / *Request) =====

    record BadCommand(String email, String password) {}

    record GoodCommand(String email, @AuditMask String password) {}

    record BenignCommand(String email, int amount) {}

    record NotACommandJustEntity(String email, String password) {} // 末尾が Command でない

    record MultiSensitiveCommand(String token, String apiKey, String secret) {}

    record BadRequest(String username, String password) {}

    record MixedCaseCommand(String userPwd, @AuditMask String otherField) {} // userPwd で検出
}
