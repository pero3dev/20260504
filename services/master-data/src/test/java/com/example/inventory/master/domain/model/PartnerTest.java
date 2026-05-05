package com.example.inventory.master.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.inventory.master.domain.event.PartnerCreatedEvent;

class PartnerTest {

    private static final PartnerId ID = new PartnerId(300_000_000_001L);
    private static final PartnerCode CODE = new PartnerCode("PT-ACME-CORP");

    @Test
    void create_は_PartnerCreatedEvent_を発行する() {
        Partner p = Partner.create(ID, CODE, "Acme 株式会社", "CUSTOMER", "ops@acme.example.com");

        assertThat(p.id()).isEqualTo(ID);
        assertThat(p.code()).isEqualTo(CODE);
        assertThat(p.name()).isEqualTo("Acme 株式会社");
        assertThat(p.partnerType()).isEqualTo("CUSTOMER");
        assertThat(p.contactEmail()).isEqualTo("ops@acme.example.com");
        assertThat(p.version()).isZero();
        assertThat(p.pendingEvents()).hasSize(1);
        assertThat(p.pendingEvents().get(0)).isInstanceOf(PartnerCreatedEvent.class);

        PartnerCreatedEvent event = (PartnerCreatedEvent) p.pendingEvents().get(0);
        assertThat(event.code()).isEqualTo(CODE.value());
        assertThat(event.versionAfter()).isEqualTo(1L);
    }

    @Test
    void 任意フィールドは_null_でも空文字に正規化される() {
        Partner p = Partner.create(ID, CODE, "取引先", null, null);
        assertThat(p.partnerType()).isEmpty();
        assertThat(p.contactEmail()).isEmpty();
    }

    @Test
    void 名前が空ならエラー() {
        assertThatThrownBy(() -> Partner.create(ID, CODE, "", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Partner 名");
    }

    @Test
    void PartnerCode_は大文字英数とハイフンのみ() {
        assertThatThrownBy(() -> new PartnerCode("pt-lower"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PartnerCode("PT"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PartnerCode("PT 001"))
                .isInstanceOf(IllegalArgumentException.class);
        new PartnerCode("PT-001");
        new PartnerCode("CARRIER-YAMATO-1");
    }
}
