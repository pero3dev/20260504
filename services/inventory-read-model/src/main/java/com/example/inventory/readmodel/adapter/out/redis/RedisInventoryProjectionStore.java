package com.example.inventory.readmodel.adapter.out.redis;

import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.example.inventory.commons.tenant.TenantContext;
import com.example.inventory.readmodel.application.port.out.InventoryProjectionStore;
import com.example.inventory.readmodel.domain.model.InventoryProjection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis ベースの投影ストア。キー形式は {@code tenant:{tenantId}:inventory:{id}}。 値は {@link InventoryProjection} の
 * JSON 直列化。
 *
 * <p>テナント分離はキー名前空間で実現。{@link TenantContext} を必須とする (未設定時は呼び出し側のバグ → IllegalStateException で
 * fail-fast)。
 */
@Repository
public class RedisInventoryProjectionStore implements InventoryProjectionStore {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisInventoryProjectionStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<InventoryProjection> findById(long inventoryId) {
        String json = redis.opsForValue().get(key(inventoryId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, InventoryProjection.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Redis 上の投影 JSON をデシリアライズ失敗 inventoryId=" + inventoryId, e);
        }
    }

    @Override
    public void save(InventoryProjection projection) {
        try {
            redis.opsForValue()
                    .set(key(projection.id()), objectMapper.writeValueAsString(projection));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("投影のシリアライズに失敗 inventoryId=" + projection.id(), e);
        }
    }

    private static String key(long inventoryId) {
        return "tenant:" + TenantContext.required().value() + ":inventory:" + inventoryId;
    }
}
