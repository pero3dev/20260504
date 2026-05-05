package com.example.inventory.commons.audit;

/**
 * 監査対象操作の結末種別。
 *
 * <p>J-SOX では失敗した操作も統制上重要(誰が何を試みたか)。 AuditableAspect は例外有無を判別してこの値を埋める。
 */
public enum AuditOutcome {
    /** 正常終了。 */
    SUCCESS,
    /** 業務ルール違反({@code BusinessException} 派生)。 */
    BUSINESS_FAILURE,
    /** システム例外(想定外)。 */
    SYSTEM_FAILURE
}
