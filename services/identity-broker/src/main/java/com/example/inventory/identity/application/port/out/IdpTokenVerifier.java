package com.example.inventory.identity.application.port.out;

/**
 * 外部 IdP(Cognito 等)が発行した access token を検証し、 内部 User を引くキーを返す出力ポート。
 *
 * <p>adapter 実装(Nimbus + remote JWKS)は signature / iss / aud / exp / nbf を検証する。 unit test では memory
 * 実装で差替える。
 */
public interface IdpTokenVerifier {

    /**
     * @param accessToken 外部 IdP の JWT 文字列
     * @return 検証結果。 内部 User と紐付ける subject claim の値(Cognito では `email` または `sub`)
     * @throws InvalidIdpTokenException 署名 / 期限 / iss / aud いずれかが不正
     */
    Subject verify(String accessToken);

    /** 検証成功時の subject 表現。 internal User とのマッピングは UseCase 側で行う(emails 比較等)。 */
    record Subject(String value, String issuer) {
        public Subject {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("subject value is required");
            }
            if (issuer == null || issuer.isBlank()) {
                throw new IllegalArgumentException("issuer is required");
            }
        }
    }

    class InvalidIdpTokenException extends RuntimeException {
        public InvalidIdpTokenException(String message) {
            super(message);
        }

        public InvalidIdpTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
