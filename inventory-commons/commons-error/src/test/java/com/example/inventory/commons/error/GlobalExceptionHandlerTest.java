package com.example.inventory.commons.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/** GlobalExceptionHandler の単体テスト(コントローラ非依存)。 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void BusinessExceptionは指定したステータスとerrorCodeで返却される() {
        ResponseEntity<ProblemDetail> res = handler.handleBusiness(new SampleBusinessError());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getProperties()).containsEntry("errorCode", "ERR_SAMPLE");
        assertThat(res.getBody().getDetail()).contains("業務エラー");
    }

    @Test
    void StepUpRequiredExceptionは401_WWWAuthenticate付きで返却される() {
        ResponseEntity<ProblemDetail> res = handler.handleStepUp(new StepUpRequiredException());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE))
                .containsExactly("MFA-Required realm=\"step-up\"");
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getProperties())
                .containsEntry("errorCode", StepUpRequiredException.CODE);
    }

    @Test
    void IllegalArgumentExceptionは400で返却される() {
        ResponseEntity<ProblemDetail> res =
                handler.handleIllegalArgument(new IllegalArgumentException("invalid id"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getProperties()).containsEntry("errorCode", "ERR_BAD_ARGUMENT");
        assertThat(res.getBody().getDetail()).isEqualTo("invalid id");
    }

    @Test
    void 想定外例外は500_詳細をレスポンスに漏らさない() {
        ResponseEntity<ProblemDetail> res =
                handler.handleUnexpected(new RuntimeException("DBコネクションプール枯渇 詳細パス情報"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getDetail()).isEqualTo("サーバ内部エラーが発生しました");
        // 元の例外メッセージはレスポンスに含まれないこと
        assertThat(res.getBody().getDetail()).doesNotContain("DBコネクションプール枯渇");
    }

    /** テスト用 BusinessException。 */
    private static class SampleBusinessError extends BusinessException {
        SampleBusinessError() {
            super("業務エラーが発生しました");
        }

        @Override
        public String errorCode() {
            return "ERR_SAMPLE";
        }
    }
}
