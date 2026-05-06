package com.example.inventory.hub.adapter.in.kafka;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 注文確定イベントを 1 明細 = 1 CSV 行 に変換する。
 *
 * <p>列順:
 *
 * <pre>
 *   order_code, customer_email, currency, total_amount, occurred_at,
 *   line_no, sku_code, location_id, quantity, unit_price
 * </pre>
 *
 * <p>カンマ / 改行 / ダブルクォートを含む値は RFC 4180 準拠のクォートでエスケープ。 customerEmail は PII
 * のため、本実装では「ドメイン部分のみ」の派生情報は出さず、 上流の masking 規約に従ってそのまま出す。実 CSV エクスポート要件で 異なる場合は本クラスで変換する。
 */
public class RetailOrderCsvFormatter {

    /** 1 イベント = 複数明細を CSV 行リストに変換。order ヘッダ部分は明細毎に繰り返す(denormalized 出力)。 */
    public List<String> format(RetailOrderPlacedMessage msg) {
        List<String> rows = new ArrayList<>(msg.items().size());
        String orderHeader =
                csv(msg.code())
                        + ","
                        + csv(msg.customerEmail())
                        + ","
                        + csv(msg.currency())
                        + ","
                        + decimalOrEmpty(msg.totalAmount())
                        + ","
                        + (msg.occurredAt() == null ? "" : msg.occurredAt().toString());
        for (RetailOrderPlacedMessage.Line line : msg.items()) {
            rows.add(
                    orderHeader
                            + ","
                            + line.lineNo()
                            + ","
                            + csv(line.skuCode())
                            + ","
                            + csv(line.locationId())
                            + ","
                            + line.quantity()
                            + ","
                            + decimalOrEmpty(line.unitPrice()));
        }
        return rows;
    }

    private static String decimalOrEmpty(BigDecimal v) {
        return v == null ? "" : v.toPlainString();
    }

    /** RFC 4180: 値内に , " 改行を含めばクォート、" は "" にエスケープ。 */
    private static String csv(String value) {
        if (value == null) return "";
        boolean needsQuote =
                value.indexOf(',') >= 0
                        || value.indexOf('"') >= 0
                        || value.indexOf('\n') >= 0
                        || value.indexOf('\r') >= 0;
        if (!needsQuote) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
