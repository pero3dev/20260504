package com.example.inventory.commons.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * リポジトリ書込を伴うが {@link Auditable} を意図的に付与しない事を宣言するマーカ。 {@code @Auditable} と区別し、 {@code reason}
 * で例外理由をコード上に固着させる。
 *
 * <p>典型ケース:
 *
 * <ul>
 *   <li><b>audit emitter 自体</b>(例: {@code ProcessAuditEventService}, {@code
 *       ComputeDailyMerkleAnchorService}):自分自身の監査を発行する場所で {@code @Auditable} を付けると、 audit 発行が audit
 *       を生み再帰ループになる
 *   <li><b>read model projection</b>(例: {@code ApplyInventoryMovementService}, {@code
 *       IngestOrderPlacedService}):元イベントが発生源 service で audit 済のため、 投影側でも audit を打つと一操作 =
 *       複数監査記録の二重カウントになる
 *   <li><b>定期バッチの内部 housekeeping</b>(例: 期限切れ workflow expire):業務実態は run-time 変化だが、 統制上は scheduler
 *       の責務でありユーザ操作ではない
 * </ul>
 *
 * <p>{@code AuditableAspect} は {@code @AuditExempt} を見ない(無視する)。 これは ArchUnit ルール{@code
 * writePathsAreAuditable} 用の compliance マーカで、 ランタイム挙動には影響しない。
 *
 * <p>使用条件:{@code reason} は必須(空文字不可)。 PR レビュアと監査担当が exempt 判断の根拠を確認できるようにするため。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditExempt {

    /** 例外理由(空文字不可)。 例: "audit emitter 自身の自己再帰防止"。 */
    String reason();
}
