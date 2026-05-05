package com.example.inventory.commons.audit;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * {@link AuditMask} 付きフィールドの値を、設定されたマスク文字列で置き換えて出力する Jackson Serializer。 audit ペイロードの ObjectMapper
 * にのみ適用される(通常の API レスポンスには影響しない)。
 */
public class AuditMaskingSerializer extends StdSerializer<Object> {

    private final String maskValue;

    public AuditMaskingSerializer(String maskValue) {
        super(Object.class);
        this.maskValue = maskValue;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        // null は素直に null として出力(マスクは「ある値を隠す」意味)
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeString(maskValue);
        }
    }
}
