package com.example.inventory.readmodel.domain.model;

import java.time.Instant;

/**
 * 在庫投影(Read Model)。Inventory Core が発行する {@code inventory.movement.v1} イベントを適用して構築される。
 *
 * <p>{@code version} は Inventory Core 集約の version と一致。 同じ version のイベントが再配信された場合は冪等にスキップする({@link
 * #isStaleAgainst} を投影更新前にチェックする)。
 */
public record InventoryProjection(
        long id,
        String skuId,
        String locationId,
        int available,
        int reserved,
        long version,
        Instant lastUpdatedAt) {

    /** 既に適用済みのイベントを再度適用しようとしているか判定する。 Read Model に対して同じ version 以下のイベントが届いた場合は冪等にスキップ。 */
    public boolean isStaleAgainst(long incomingVersion) {
        return incomingVersion <= this.version;
    }
}
