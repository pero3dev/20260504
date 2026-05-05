package com.example.inventory.core.adapter.out.persistence;

/** MyBatis マッパーとリポジトリ実装の間で受け渡されるフラットな行表現。 adapter パッケージに置くことで、ドメイン層を永続化の関心事から切り離す。 */
public record InventoryRow(
        long id, String skuId, String locationId, int available, int reserved, long version) {}
