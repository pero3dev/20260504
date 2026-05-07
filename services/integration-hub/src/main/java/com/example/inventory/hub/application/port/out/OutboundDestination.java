package com.example.inventory.hub.application.port.out;

import java.util.List;

import com.example.inventory.commons.tenant.TenantId;

/**
 * 外部システムへ出力レコードを届ける抽象ポート。
 *
 * <p>実装例:
 *
 * <ul>
 *   <li>{@code LocalFileDestination}: ローカルファイルに追記(MVP / テスト用)
 *   <li>{@code S3Destination}: S3 に 1 オブジェクト = 1 batch で PUT(A3)
 *   <li>SftpDestination: SFTP 経由で取引先に送信(将来)
 *   <li>AS2Destination: Apache Camel + AS2 で EDI 送信(将来)
 * </ul>
 */
public interface OutboundDestination {

    /** 1 レコード = 1 行を {@code tenantId} の宛先に追記。冪等性は実装(or 外部側)が担う。 */
    void write(TenantId tenantId, String formattedLine);

    /**
     * 複数レコードをまとめて出力する(A3、 S3 のような 1 オブジェクト = 1 PUT 系のため)。
     *
     * <p>default 実装は逐次 {@link #write} を呼ぶ(LocalFile / SFTP append 系で都合がよい)。 S3 のような pay-per-PUT 系は
     * override してバッチ化することで PUT 数を最小化できる。
     */
    default void writeBatch(TenantId tenantId, List<String> formattedLines) {
        for (String line : formattedLines) {
            write(tenantId, line);
        }
    }
}
