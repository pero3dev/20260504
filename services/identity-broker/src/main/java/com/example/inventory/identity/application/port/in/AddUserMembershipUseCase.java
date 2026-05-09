package com.example.inventory.identity.application.port.in;

import com.example.inventory.identity.domain.model.TenantMembership;

/**
 * 既存 user に追加 tenant membership を作成する use case(A5 follow-up¹³)。
 *
 * <p>follow-up¹² の {@link RegisterUserUseCase} は新規 user + 初期 1 membership を生成するが、 user が複数テナントに所属する
 * (例: 親会社 admin が子会社テナントにも触る) ケースに対応するための補完。 既存 user に対して別 tenant の追加 membership を貼る。
 */
public interface AddUserMembershipUseCase {

    /**
     * @throws UserNotFoundException 該当 userId の user が存在しない(404)
     * @throws TenantNotFoundException 該当 tenantId の tenant が存在しないか DEACTIVATED(404)
     * @throws UserMembershipAlreadyExistsException 同 (userId, tenantId) の membership が既存(409)
     * @throws IllegalArgumentException tenantId / roleName 形式不正(GlobalExceptionHandler が 400 化)
     */
    TenantMembership addMembership(Command command);

    /**
     * @param userId 既存 Snowflake user id
     * @param tenantId 追加する tenant id
     * @param roleName この tenant 内での role(例: VIEWER, INVENTORY_MANAGER, SUPER_ADMIN)
     */
    record Command(long userId, String tenantId, String roleName) {}
}
