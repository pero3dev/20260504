package com.example.inventory.master.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.inventory.master.domain.event.SkuCreatedEvent;

class SkuTest {

    private static final SkuId ID = new SkuId(100_000_000_001L);
    private static final SkuCode CODE = new SkuCode("SKU-COCA-COLA-500ML");

    @Test
    void create_は_SkuCreatedEvent_を発行する() {
        Sku sku = Sku.create(ID, CODE, "コカ・コーラ 500ml", "炭酸飲料", "BOTTLE");

        assertThat(sku.id()).isEqualTo(ID);
        assertThat(sku.code()).isEqualTo(CODE);
        assertThat(sku.name()).isEqualTo("コカ・コーラ 500ml");
        assertThat(sku.unitOfMeasure()).isEqualTo("BOTTLE");
        assertThat(sku.version()).isZero(); // save 前
        assertThat(sku.pendingEvents()).hasSize(1);
        assertThat(sku.pendingEvents().get(0)).isInstanceOf(SkuCreatedEvent.class);

        SkuCreatedEvent event = (SkuCreatedEvent) sku.pendingEvents().get(0);
        assertThat(event.code()).isEqualTo(CODE.value());
        assertThat(event.versionAfter()).isEqualTo(1L); // post-state
    }

    @Test
    void 名前が空ならエラー() {
        assertThatThrownBy(() -> Sku.create(ID, CODE, "", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU 名");
    }

    @Test
    void 名前が_200_文字超過ならエラー() {
        String longName = "あ".repeat(201);
        assertThatThrownBy(() -> Sku.create(ID, CODE, longName, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void SkuCode_は大文字英数とハイフンのみ() {
        assertThatThrownBy(() -> new SkuCode("sku-lower"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SkuCode("SK"))
                .isInstanceOf(IllegalArgumentException.class); // 短すぎ
        assertThatThrownBy(() -> new SkuCode("SKU 001"))
                .isInstanceOf(IllegalArgumentException.class); // 空白
        // 合格
        new SkuCode("SKU-001");
        new SkuCode("ABC-123-XYZ");
    }
}
