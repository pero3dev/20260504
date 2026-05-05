package com.example.inventory.commons.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 監査イベントの {@code inputJson} に出力する際にマスクするフィールドを示す。
 *
 * <p>パスワード・カード番号・SSN 等の機微情報をユースケースのコマンド/リクエスト型に持つ場合、 該当フィールドにこのアノテーションを付与する。{@link AuditableAspect}
 * が使う ObjectMapper には {@link AuditMaskingModule} が登録されており、該当フィールドは {@code "***"} などに置換されて audit
 * ストアに記録される。
 *
 * <p>適用例(record):
 *
 * <pre>{@code
 * public record AuthenticateCommand(String email, @AuditMask String password) { ... }
 * }</pre>
 *
 * <p>本アノテーションは **audit ペイロード専用**。アプリケーションの通常レスポンスでは マスクされない(別の {@code ObjectMapper} を使うため)。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({
    ElementType.FIELD,
    ElementType.METHOD,
    ElementType.PARAMETER,
    ElementType.RECORD_COMPONENT
})
public @interface AuditMask {

    /** マスク後の表示値。既定 {@code "***"}。長さ確認だけしたい場合は {@code "[8 chars]"} 等も可。 */
    String value() default "***";
}
