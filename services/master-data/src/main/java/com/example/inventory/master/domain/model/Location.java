package com.example.inventory.master.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.master.domain.event.LocationCreatedEvent;

/**
 * Location 集約ルート。テナント内の拠点(倉庫 / 店舗 / 製造拠点)1 件を表す。
 *
 * <p>純粋な POJO(ADR-0009)。MyBatis/JPA のアノテーション無し。 楽観ロック用の {@code version} を持ち、save 時は +1
 * される(commons-persistence 規約)。
 *
 * <p>{@code locationType} は 自由文字列(WAREHOUSE / STORE / FACTORY / DC 等)。 MVP では enum
 * 化せず、業態側のコンテキストでの解釈に委ねる。
 */
public final class Location {

    private final LocationId id;
    private final LocationCode code;
    private String name;
    private String addressLine;
    private String locationType;
    private long version;

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    public static Location restore(
            LocationId id,
            LocationCode code,
            String name,
            String addressLine,
            String locationType,
            long version) {
        return new Location(id, code, name, addressLine, locationType, version);
    }

    /** 新規 Location を作成する。{@link LocationCreatedEvent} を発行する。 */
    public static Location create(
            LocationId id,
            LocationCode code,
            String name,
            String addressLine,
            String locationType) {
        Location location = new Location(id, code, name, addressLine, locationType, 0L);
        location.pendingEvents.add(
                new LocationCreatedEvent(
                        id.value(),
                        code.value(),
                        name,
                        location.addressLine,
                        location.locationType,
                        location.version + 1,
                        java.time.Instant.now()));
        return location;
    }

    private Location(
            LocationId id,
            LocationCode code,
            String name,
            String addressLine,
            String locationType,
            long version) {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException("Location 名が不正(空 or 200 文字超過)");
        }
        this.id = id;
        this.code = code;
        this.name = name;
        this.addressLine = addressLine == null ? "" : addressLine;
        this.locationType = locationType == null ? "" : locationType;
        this.version = version;
    }

    public LocationId id() {
        return id;
    }

    public LocationCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public String addressLine() {
        return addressLine;
    }

    public String locationType() {
        return locationType;
    }

    public long version() {
        return version;
    }

    public List<DomainEvent> pendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    public void clearPendingEvents() {
        pendingEvents.clear();
    }
}
