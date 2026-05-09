package com.example.inventory.identity.application.port.in;

import com.example.inventory.identity.domain.model.User;

/**
 * User 全体を DEACTIVATED に遷移させる use case(A5 follow-up¹⁵)。
 *
 * <p>{@link RemoveUserMembershipUseCase} (¹⁴) は特定 tenant への アクセス取消、 本 use case は user 自身を無効化 (全
 * membership 一括無効化と同等の効果)。 DEACTIVATED user は AuthenticateService / ExchangeFederatedTokenService
 * が認証失敗で弾く。 既発行 access token は TTL 切れまで有効。
 *
 * <p>冪等。 既に DEACTIVATED の user に対して呼んでも 200 を返す(state 維持、 deactivatedAt は最初の値を保持)。
 */
public interface DeactivateUserUseCase {

    /**
     * @throws UserNotFoundException 該当 userId の user が存在しない(404)
     */
    User deactivate(long userId);
}
