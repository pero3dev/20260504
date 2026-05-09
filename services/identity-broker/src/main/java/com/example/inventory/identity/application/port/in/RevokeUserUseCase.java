package com.example.inventory.identity.application.port.in;

/**
 * 既発行 access token を即時無効化する admin 操作(A5 follow-up¹⁹、 ADR-0023)。
 *
 * <p>{@link DeactivateUserUseCase} と異なり、 user 自身は ACTIVE のまま。 トークン漏洩疑い / 端末紛失等で「user は使い続けるが 既発行の
 * token だけ即時無効化したい」 ケースに対応する。 user lifecycle イベント駆動の自動 revoke (deactivate / removeMembership /
 * tenant deactivate fanout) と対比して、 明示的な人手 break-glass 操作。
 *
 * <p>冪等。 既に revoke 中でも重ねて呼べる(TTL は呼出時刻基準で再設定される、 defense-in-depth)。 reason は audit chain に 記録するが
 * Redis には載らない。
 */
public interface RevokeUserUseCase {

    /**
     * @param userId 対象 user id(該当無しなら {@link UserNotFoundException} で 404)
     * @param reason J-SOX 監査用、 1..512 字。 空白のみは不可
     * @throws UserNotFoundException 該当 userId の user が存在しない
     */
    void revoke(long userId, String reason);
}
