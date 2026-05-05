package com.example.inventory.commons.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class AuditMaskingModuleTest {

    private final ObjectMapper plainMapper = new ObjectMapper();
    private final ObjectMapper maskingMapper =
            new ObjectMapper().registerModule(new AuditMaskingModule());

    @Test
    void マスキングモジュール無しでは生の値が出る() throws Exception {
        Credentials creds = new Credentials("alice@example.com", "secret-pw");
        String json = plainMapper.writeValueAsString(creds);
        assertThat(json).contains("\"password\":\"secret-pw\"");
    }

    @Test
    void マスキングモジュール有りでは指定マスク値に置換される() throws Exception {
        Credentials creds = new Credentials("alice@example.com", "secret-pw");
        String json = maskingMapper.writeValueAsString(creds);

        assertThat(json).contains("\"email\":\"alice@example.com\""); // 非機微はそのまま
        assertThat(json).contains("\"password\":\"***\""); // マスク済み
        assertThat(json).doesNotContain("secret-pw"); // 元の値は漏れない
    }

    @Test
    void マスク文字列をカスタマイズできる() throws Exception {
        WithCustomMask data = new WithCustomMask("token-abcdef");
        String json = maskingMapper.writeValueAsString(data);
        assertThat(json).contains("\"token\":\"<redacted>\"");
        assertThat(json).doesNotContain("token-abcdef");
    }

    @Test
    void null値はマスクではなくnullで出力される() throws Exception {
        Credentials creds = new Credentials("alice@example.com", null);
        String json = maskingMapper.writeValueAsString(creds);
        assertThat(json).contains("\"password\":null");
    }

    @Test
    void マスキングモジュールは独立_オリジナルmapperは影響を受けない() throws Exception {
        ObjectMapper base = new ObjectMapper();
        ObjectMapper masked = base.copy().registerModule(new AuditMaskingModule());

        Credentials creds = new Credentials("alice@example.com", "secret-pw");
        assertThat(masked.writeValueAsString(creds)).contains("\"password\":\"***\"");
        assertThat(base.writeValueAsString(creds)).contains("\"password\":\"secret-pw\"");
    }

    /** テスト用 record。Java records は AuditMask アノテーションをアクセサに propagate する。 */
    record Credentials(String email, @AuditMask String password) {}

    record WithCustomMask(@AuditMask("<redacted>") String token) {}
}
