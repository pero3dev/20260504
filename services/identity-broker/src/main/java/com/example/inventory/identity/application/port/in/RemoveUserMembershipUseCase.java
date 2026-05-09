package com.example.inventory.identity.application.port.in;

/**
 * 既存 user の tenant membership を取り消す use case(A5 follow-up¹⁴)。
 *
 * <p>follow-up¹³ の {@link AddUserMembershipUseCase} と対をなす offboarding 経路。 該当 membership を物理削除する
 * (soft delete はしない、 audit trail は audit-service 側に残るため履歴は確保される)。
 *
 * <p><b>tenant 全体の deactivate との違い:</b> {@code TenantManagementService.deactivate(tenantId)} は
 * 「テナント全体を非活性化」 (新規 access token 発行を全 user について止める) するが、 本 use case は 「特定 user 1 人の特定 tenant
 * へのアクセスだけ取り消す」 (既発行 access token は TTL 切れまで有効、 follow-up² の audit がカバーする)。
 */
public interface RemoveUserMembershipUseCase {

    /**
     * @throws UserMembershipNotFoundException 該当 (userId, tenantId) の membership が無い(404)
     * @throws IllegalArgumentException tenantId 形式不正(GlobalExceptionHandler が 400 化)
     */
    void removeMembership(Command command);

    record Command(long userId, String tenantId) {}
}
