package com.example.inventory.identity.domain.model;

/** ユーザー状態(A5 follow-up¹⁵)。 ACTIVE → DEACTIVATED の一方向遷移のみ(復活は明示的に再 register する想定で別 use case)。 */
public enum UserStatus {
    ACTIVE,
    DEACTIVATED
}
