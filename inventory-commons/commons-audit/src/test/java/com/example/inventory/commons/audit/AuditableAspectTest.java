package com.example.inventory.commons.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import com.example.inventory.commons.error.BusinessException;
import com.example.inventory.commons.tenant.TenantContext;
import com.example.inventory.commons.tenant.TenantId;
import com.fasterxml.jackson.databind.ObjectMapper;

/** AuditableAspect の統合テスト。AspectJ プロキシ経由でアスペクトの振る舞いを検証する。 */
class AuditableAspectTest {

    private AuditEventEmitter emitter;
    private TestService proxy;

    @BeforeEach
    void setUp() {
        emitter = mock(AuditEventEmitter.class);
        AuditableAspect aspect = new AuditableAspect(emitter, new ObjectMapper());
        AspectJProxyFactory factory = new AspectJProxyFactory(new TestService());
        factory.addAspect(aspect);
        proxy = factory.getProxy();
        TenantContext.set(new TenantId("acme"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void 成功時にSUCCESSの監査イベントが発行される() {
        proxy.reserve(new ReserveCommand(123L, 5));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(emitter, times(1)).emit(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.action()).isEqualTo("INVENTORY_RESERVE");
        assertThat(event.targetType()).isEqualTo("Inventory");
        assertThat(event.targetId()).isEqualTo("123");
        assertThat(event.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.errorCode()).isNull();
        assertThat(event.operatorTenantId()).isEqualTo("acme");
        assertThat(event.read()).isFalse();
        assertThat(event.inputJson()).contains("\"inventoryId\":123").contains("\"quantity\":5");
    }

    @Test
    void 業務例外時はBUSINESS_FAILUREで監査が出てから元の例外が再送出される() {
        assertThatThrownBy(() -> proxy.fail()).isInstanceOf(InsufficientStockTestException.class);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(emitter, times(1)).emit(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.outcome()).isEqualTo(AuditOutcome.BUSINESS_FAILURE);
        assertThat(event.errorCode()).isEqualTo("ERR_TEST_INSUFFICIENT");
    }

    @Test
    void システム例外時はSYSTEM_FAILUREで監査される() {
        assertThatThrownBy(() -> proxy.crash()).isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(emitter, times(1)).emit(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.outcome()).isEqualTo(AuditOutcome.SYSTEM_FAILURE);
        assertThat(event.errorCode()).isEqualTo("SYSTEM_ERROR");
    }

    @Test
    void 監査発行が落ちても元のメソッドの結果は返る() {
        doThrow(new RuntimeException("kafka down")).when(emitter).emit(any());

        // emit が落ちても reserve は値を返すこと
        long result = proxy.reserve(new ReserveCommand(7L, 1));
        assertThat(result).isEqualTo(7L);
    }

    @Test
    void Auditable無しのメソッドは横取りされない() {
        proxy.notAudited();
        verify(emitter, never()).emit(any());
    }

    @Test
    void AuditMask付きフィールドはinputJsonでマスクされる() {
        proxy.login(new LoginCommand("alice@example.com", "super-secret-password"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(emitter, times(1)).emit(captor.capture());
        AuditEvent event = captor.getValue();

        assertThat(event.inputJson()).contains("\"email\":\"alice@example.com\""); // 非機微
        assertThat(event.inputJson()).contains("\"password\":\"***\""); // マスク済み
        assertThat(event.inputJson()).doesNotContain("super-secret-password"); // 漏洩防止
    }

    /** テスト対象のサービス。 */
    static class TestService {

        @Auditable(
                action = "INVENTORY_RESERVE",
                targetType = "Inventory",
                targetIdExpression = "#command.inventoryId")
        public long reserve(ReserveCommand command) {
            return command.inventoryId();
        }

        @Auditable(action = "INVENTORY_RESERVE", targetType = "Inventory")
        public void fail() {
            throw new InsufficientStockTestException();
        }

        @Auditable(action = "INVENTORY_RESERVE", targetType = "Inventory")
        public void crash() {
            throw new IllegalStateException("予期せぬ障害");
        }

        @Auditable(
                action = "USER_AUTHENTICATE",
                targetType = "User",
                targetIdExpression = "#command.email")
        public void login(LoginCommand command) {
            // no-op for test
        }

        public void notAudited() {
            // no-op
        }
    }

    /** テスト用コマンド。 */
    public record ReserveCommand(long inventoryId, int quantity) {}

    /** マスキングテスト用コマンド。{@code password} に @AuditMask が付いている。 */
    public record LoginCommand(String email, @AuditMask String password) {}

    /** テスト用業務例外。 */
    static class InsufficientStockTestException extends BusinessException {
        InsufficientStockTestException() {
            super("test");
        }

        @Override
        public String errorCode() {
            return "ERR_TEST_INSUFFICIENT";
        }
    }
}
