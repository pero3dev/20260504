package com.example.inventory.workflow.domain.model;

/**
 * 承認アクション(A1、 ADR-0015 follow-up)。
 *
 * <ul>
 *   <li>{@link #APPROVE} → 現 step を complete、 次があれば次へ進む
 *   <li>{@link #REJECT} → 現 step を fail、 全体 FAILED
 *   <li>{@link #SKIP} → 現 step を complete として扱い次へ(中断審査の skip パス)
 * </ul>
 */
public enum ApprovalAction {
    APPROVE,
    REJECT,
    SKIP
}
