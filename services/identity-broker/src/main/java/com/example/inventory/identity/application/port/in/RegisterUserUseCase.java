package com.example.inventory.identity.application.port.in;

import com.example.inventory.identity.domain.model.User;

/**
 * 管理者向けの user 登録 use case(A5 follow-up¹²)。
 *
 * <p>federation-only 経路の user を pre-create する。 password sentinel を貼るため email + password 認証では 通れず、
 * SAML 連携 で同 email が来たときに 既存 user として認識される(JIT 経路を踏まずに常識的なログイン flow)。 同時に初期 TenantMembership を作るので、
 * admin が「Bob を acme テナントの INVENTORY_MANAGER として作る」 という 1 操作で完結。
 */
public interface RegisterUserUseCase {

    /**
     * @throws UserAlreadyExistsException 同 email の user が既存(409)
     * @throws TenantNotFoundException 該当 tenantId の tenant が存在しないか DEACTIVATED(404)
     * @throws IllegalArgumentException 不正な email / role 形式 等(GlobalExceptionHandler が 400 化)
     */
    User register(Command command);

    /**
     * @param email login で使う email(SAML subject claim と照合)
     * @param displayName UI 表示名
     * @param tenantId 初期 membership の tenant
     * @param roleName 初期 role(例: VIEWER, INVENTORY_MANAGER, SUPER_ADMIN)
     */
    record Command(String email, String displayName, String tenantId, String roleName) {}
}
