package com.example.inventory.master.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.master.domain.event.SkuCreatedEvent;

/**
 * SKU 集約ルート。テナント内の商品マスタ1件を表す。
 *
 * <p>純粋な POJO(ADR-0009)。MyBatis/JPA のアノテーション無し。 楽観ロック用の {@code version} を持ち、save 時は +1
 * される(commons-persistence 規約)。
 */
public final class Sku {

    private final SkuId id;
    private final SkuCode code;
    private String name;
    private String description;
    private String unitOfMeasure;
    private long version;

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    public static Sku restore(
            SkuId id,
            SkuCode code,
            String name,
            String description,
            String unitOfMeasure,
            long version) {
        return new Sku(id, code, name, description, unitOfMeasure, version);
    }

    /** 新規 SKU を作成する。{@link SkuCreatedEvent} を発行する。 */
    public static Sku create(
            SkuId id, SkuCode code, String name, String description, String unitOfMeasure) {
        Sku sku = new Sku(id, code, name, description, unitOfMeasure, 0L);
        sku.pendingEvents.add(
                new SkuCreatedEvent(
                        id.value(),
                        code.value(),
                        name,
                        description,
                        unitOfMeasure,
                        sku.version + 1,
                        java.time.Instant.now()));
        return sku;
    }

    private Sku(
            SkuId id,
            SkuCode code,
            String name,
            String description,
            String unitOfMeasure,
            long version) {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException("SKU 名が不正(空 or 200 文字超過)");
        }
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description == null ? "" : description;
        this.unitOfMeasure = unitOfMeasure == null ? "" : unitOfMeasure;
        this.version = version;
    }

    public SkuId id() {
        return id;
    }

    public SkuCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String unitOfMeasure() {
        return unitOfMeasure;
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
