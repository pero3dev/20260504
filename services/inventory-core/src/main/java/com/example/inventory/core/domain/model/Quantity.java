package com.example.inventory.core.domain.model;

/**
 * 非負整数の数量を表す値オブジェクト。
 *
 * <p>本層では整数単位のみ扱う。kg や m など分数単位は、SKU ごとに定めた 基本単位への変換をアプリケーション側で行ってから本層に渡すこと。
 */
public record Quantity(int value) {

    public Quantity {
        if (value < 0) {
            throw new IllegalArgumentException("Quantity は非負である必要があります(指定値: " + value + ")");
        }
    }

    public static final Quantity ZERO = new Quantity(0);

    public Quantity plus(Quantity other) {
        return new Quantity(Math.addExact(this.value, other.value));
    }

    public Quantity minus(Quantity other) {
        return new Quantity(Math.subtractExact(this.value, other.value));
    }

    public boolean isAtLeast(Quantity other) {
        return this.value >= other.value;
    }
}
