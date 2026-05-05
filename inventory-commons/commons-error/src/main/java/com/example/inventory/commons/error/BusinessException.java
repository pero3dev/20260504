package com.example.inventory.commons.error;

import org.springframework.http.HttpStatus;

/**
 * 業務ルール違反の基底例外。RFC 7807 の 4xx レスポンスにマッピングされる。 各サブクラスは安定した {@link #errorCode()} を返し、フロントエンドは このコードを
 * i18n のメッセージ辞書キーとして利用する (F2 i18n方針: API は文字列ではなくエラーコードを返す)。
 *
 * <p>HTTP ステータスは {@link #statusCode()} で個別に上書き可能。 既定は {@link HttpStatus#CONFLICT}(409) —
 * 「現在の状態でリクエストを処理できない」が 多くの業務ルール違反の意味として最適。リソース不存在は 404、入力不正は 400 等を サブクラスで上書きする。
 */
public abstract class BusinessException extends RuntimeException {

    protected BusinessException(String message) {
        super(message);
    }

    /** 安定したエラーコード。例: {@code "ERR_INVENTORY_INSUFFICIENT"}。 */
    public abstract String errorCode();

    /** HTTP ステータス。既定 409。 */
    public HttpStatus statusCode() {
        return HttpStatus.CONFLICT;
    }
}
