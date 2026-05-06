package com.example.inventory.hub.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class RetailOrderCsvFormatterTest {

    private final RetailOrderCsvFormatter formatter = new RetailOrderCsvFormatter();

    @Test
    void 単一明細は_1_行を出力し_すべての列が含まれる() {
        RetailOrderPlacedMessage msg =
                new RetailOrderPlacedMessage(
                        100L,
                        "ORD-1",
                        "alice@example.com",
                        "JPY",
                        new BigDecimal("1500"),
                        List.of(
                                new RetailOrderPlacedMessage.Line(
                                        1, "SKU-A", "LOC-1", 3, new BigDecimal("500"))),
                        Instant.parse("2026-05-06T10:00:00Z"));

        List<String> rows = formatter.format(msg);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .isEqualTo(
                        "ORD-1,alice@example.com,JPY,1500,2026-05-06T10:00:00Z,1,SKU-A,LOC-1,3,500");
    }

    @Test
    void 複数明細は_明細毎に_注文ヘッダを繰り返す_denormalized_出力() {
        RetailOrderPlacedMessage msg =
                new RetailOrderPlacedMessage(
                        100L,
                        "ORD-2",
                        "bob@example.com",
                        "JPY",
                        new BigDecimal("3000"),
                        List.of(
                                new RetailOrderPlacedMessage.Line(
                                        1, "SKU-A", "LOC-1", 2, new BigDecimal("500")),
                                new RetailOrderPlacedMessage.Line(
                                        2, "SKU-B", "LOC-2", 4, new BigDecimal("500"))),
                        Instant.parse("2026-05-06T10:00:00Z"));

        List<String> rows = formatter.format(msg);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).contains(",1,SKU-A,LOC-1,2,500");
        assertThat(rows.get(1)).contains(",2,SKU-B,LOC-2,4,500");
        assertThat(rows.get(0)).startsWith("ORD-2,bob@example.com,JPY,3000,");
        assertThat(rows.get(1)).startsWith("ORD-2,bob@example.com,JPY,3000,");
    }

    @Test
    void カンマや改行を含む値は_RFC4180_でクォート_エスケープされる() {
        RetailOrderPlacedMessage msg =
                new RetailOrderPlacedMessage(
                        100L,
                        "ORD-3",
                        "smith, john@example.com", // カンマ含む
                        "JPY",
                        new BigDecimal("100"),
                        List.of(
                                new RetailOrderPlacedMessage.Line(
                                        1,
                                        "SKU-\"QUOTE\"", // ダブルクォート含む
                                        "LOC-1",
                                        1,
                                        new BigDecimal("100"))),
                        Instant.parse("2026-05-06T10:00:00Z"));

        String row = formatter.format(msg).get(0);

        assertThat(row).contains("\"smith, john@example.com\"");
        assertThat(row).contains("\"SKU-\"\"QUOTE\"\"\"");
    }

    @Test
    void null_payload_は_空文字に変換される() {
        RetailOrderPlacedMessage msg =
                new RetailOrderPlacedMessage(
                        100L,
                        "ORD-4",
                        null,
                        "JPY",
                        null,
                        List.of(new RetailOrderPlacedMessage.Line(1, "SKU-A", "LOC-1", 1, null)),
                        null);

        String row = formatter.format(msg).get(0);

        // ORD-4,,JPY,,,1,SKU-A,LOC-1,1,
        assertThat(row).isEqualTo("ORD-4,,JPY,,,1,SKU-A,LOC-1,1,");
    }
}
