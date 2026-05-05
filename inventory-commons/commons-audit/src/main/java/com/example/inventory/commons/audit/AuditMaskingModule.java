package com.example.inventory.commons.audit;

import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * audit ペイロード用の Jackson モジュール。 {@link AuditMask} を見つけたら {@link AuditMaskingSerializer} に差し替える
 * AnnotationIntrospector を登録する。
 *
 * <p>本モジュールは {@code AuditableAspect} 専用の ObjectMapper にのみ登録される。 通常 API レスポンスの ObjectMapper には影響しない。
 */
public class AuditMaskingModule extends SimpleModule {

    public AuditMaskingModule() {
        super("AuditMasking");
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.appendAnnotationIntrospector(new MaskingIntrospector());
    }

    /** {@link AuditMask} を読み取って {@link AuditMaskingSerializer} を返す。 */
    static final class MaskingIntrospector extends JacksonAnnotationIntrospector {

        private static final long serialVersionUID = 1L;

        @Override
        public Object findSerializer(Annotated a) {
            AuditMask mask = a.getAnnotation(AuditMask.class);
            if (mask != null) {
                return new AuditMaskingSerializer(mask.value());
            }
            return super.findSerializer(a);
        }
    }
}
