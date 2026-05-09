package com.example.inventory.commons.test.arch;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.inventory.commons.test.arch.fixtures.application.port.out.FakeRepository;
import com.example.inventory.commons.test.arch.fixtures.application.usecase.BadNonAuditableService;
import com.example.inventory.commons.test.arch.fixtures.application.usecase.ExemptService;
import com.example.inventory.commons.test.arch.fixtures.application.usecase.GoodAuditableService;
import com.example.inventory.commons.test.arch.fixtures.application.usecase.ReadOnlyService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

/** {@link HexagonalLayerRules#writePathsAreAuditable()} の動作検証。 */
class WritePathsAreAuditableTest {

    @Test
    void 書込を呼ぶが_Auditable_無し_は違反として検出される() {
        JavaClasses classes =
                new ClassFileImporter()
                        .importClasses(BadNonAuditableService.class, FakeRepository.class);

        assertThatThrownBy(() -> HexagonalLayerRules.writePathsAreAuditable().check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("BadNonAuditableService")
                .hasMessageContaining("@Auditable");
    }

    @Test
    void 書込を呼び_Auditable_有り_は合格する() {
        JavaClasses classes =
                new ClassFileImporter()
                        .importClasses(GoodAuditableService.class, FakeRepository.class);

        assertThatCode(() -> HexagonalLayerRules.writePathsAreAuditable().check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void 書込を呼ばない_read_only_クラスは_vacuously_合格() {
        JavaClasses classes =
                new ClassFileImporter().importClasses(ReadOnlyService.class, FakeRepository.class);

        assertThatCode(() -> HexagonalLayerRules.writePathsAreAuditable().check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void 書込を呼び_AuditExempt_有り_は合格する() {
        JavaClasses classes =
                new ClassFileImporter().importClasses(ExemptService.class, FakeRepository.class);

        assertThatCode(() -> HexagonalLayerRules.writePathsAreAuditable().check(classes))
                .doesNotThrowAnyException();
    }
}
