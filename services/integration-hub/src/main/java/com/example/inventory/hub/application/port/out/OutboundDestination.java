package com.example.inventory.hub.application.port.out;

import com.example.inventory.commons.tenant.TenantId;

/**
 * 外部システムへ 1 件の出力レコードを届ける抽象ポート。
 *
 * <p>実装例:
 *
 * <ul>
 *   <li>{@code LocalFileDestination}: ローカルファイルに追記(MVP / テスト用)
 *   <li>S3Destination: S3 にオブジェクト追加(将来)
 *   <li>SftpDestination: SFTP 経由で取引先に送信(将来)
 *   <li>AS2Destination: Apache Camel + AS2 で EDI 送信(将来)
 * </ul>
 *
 * <p>本 port は「1 レコード = 1 行」の append-only 抽象。バッチ送信や形式変換は呼出側(adapter サービス)が担う。
 */
public interface OutboundDestination {

    /** 1 レコード = 1 行を {@code tenantId} の宛先に追記。冪等性は実装(or 外部側)が担う。 */
    void write(TenantId tenantId, String formattedLine);
}
