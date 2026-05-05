package com.example.inventory.master.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.inventory.master.domain.event.LocationCreatedEvent;

class LocationTest {

    private static final LocationId ID = new LocationId(200_000_000_001L);
    private static final LocationCode CODE = new LocationCode("LOC-TOKYO-A");

    @Test
    void create_は_LocationCreatedEvent_を発行する() {
        Location loc = Location.create(ID, CODE, "東京A倉庫", "東京都江東区...", "WAREHOUSE");

        assertThat(loc.id()).isEqualTo(ID);
        assertThat(loc.code()).isEqualTo(CODE);
        assertThat(loc.name()).isEqualTo("東京A倉庫");
        assertThat(loc.locationType()).isEqualTo("WAREHOUSE");
        assertThat(loc.version()).isZero();
        assertThat(loc.pendingEvents()).hasSize(1);
        assertThat(loc.pendingEvents().get(0)).isInstanceOf(LocationCreatedEvent.class);

        LocationCreatedEvent event = (LocationCreatedEvent) loc.pendingEvents().get(0);
        assertThat(event.code()).isEqualTo(CODE.value());
        assertThat(event.versionAfter()).isEqualTo(1L);
    }

    @Test
    void 任意フィールドは_null_でも空文字に正規化される() {
        Location loc = Location.create(ID, CODE, "拠点", null, null);
        assertThat(loc.addressLine()).isEmpty();
        assertThat(loc.locationType()).isEmpty();
    }

    @Test
    void 名前が空ならエラー() {
        assertThatThrownBy(() -> Location.create(ID, CODE, "", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Location 名");
    }

    @Test
    void 名前が_200_文字超過ならエラー() {
        String longName = "あ".repeat(201);
        assertThatThrownBy(() -> Location.create(ID, CODE, longName, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void LocationCode_は大文字英数とハイフンのみ() {
        assertThatThrownBy(() -> new LocationCode("loc-lower"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocationCode("LO"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocationCode("LOC 001"))
                .isInstanceOf(IllegalArgumentException.class);
        // 合格
        new LocationCode("LOC-001");
        new LocationCode("WH-TOKYO-A1");
    }
}
