package com.example.inventory.commons.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ユースケース(または統制上重要なデータの参照)が監査対象であることを宣言する。
 *
 * <p>{@code AuditableAspect} がメソッド呼び出しを横断的に補足し、 {@code audit.log.v1} トピックへ操作者ID・テナント・アクション・対象ID・
 * 変更前後・トレースIDを含むイベントを発行する(ADR-0008)。
 *
 * <p><b>ArchUnit による強制:</b> {@code commons-persistence} のリポジトリへ 書き込みを行うメソッドは、{@code @Auditable}
 * を付与されたユースケース経由で のみ到達可能であること。アノテーション付け忘れはコンプライアンス欠陥として CI失敗を起こす(ADR-0008の補完策)。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {

    /** 安定したアクションコード。例: {@code "INVENTORY_RESERVE"}。 */
    String action();

    /** 対象エンティティ種別。 */
    String targetType();

    /** メソッド引数から対象IDを取り出すための SpEL 式。 例: {@code "#command.inventoryId"}。 */
    String targetIdExpression() default "";

    /** 状態変更ではなく、統制上重要な「参照」操作の場合は true。 */
    boolean read() default false;
}
