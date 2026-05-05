package com.example.inventory.identity.application.port.in;

import org.springframework.http.HttpStatus;

import com.example.inventory.commons.error.BusinessException;

/** クレデンシャル不正 / セッショントークン無効 / ユーザー未存在を区別せずに 401 を返す。 */
public class AuthenticationFailedException extends BusinessException {

    public static final String CODE = "ERR_AUTHENTICATION_FAILED";

    public AuthenticationFailedException() {
        super("認証に失敗しました");
    }

    @Override
    public String errorCode() {
        return CODE;
    }

    @Override
    public HttpStatus statusCode() {
        return HttpStatus.UNAUTHORIZED;
    }
}
