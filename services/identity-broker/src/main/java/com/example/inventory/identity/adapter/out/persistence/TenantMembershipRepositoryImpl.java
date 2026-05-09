package com.example.inventory.identity.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.application.port.out.TenantMembershipRepository;
import com.example.inventory.identity.domain.model.RoleName;
import com.example.inventory.identity.domain.model.TenantMembership;
import com.example.inventory.identity.domain.model.UserId;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** TenantMembership 参照のための MyBatis 実装。JSONB カラムの解釈は Jackson で行う。 */
@Repository
public class TenantMembershipRepositoryImpl implements TenantMembershipRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final TenantMembershipMapper mapper;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator idGenerator;

    public TenantMembershipRepositoryImpl(
            TenantMembershipMapper mapper,
            ObjectMapper objectMapper,
            SnowflakeIdGenerator idGenerator) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public List<TenantMembership> findByUserId(UserId userId) {
        return mapper.findByUserId(userId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<TenantMembership> findByUserAndTenant(UserId userId, TenantId tenantId) {
        TenantMembershipRow row = mapper.findByUserAndTenant(userId.value(), tenantId.value());
        return row == null ? Optional.empty() : Optional.of(toDomain(row));
    }

    @Override
    public void add(TenantMembership membership) {
        long id = idGenerator.nextId();
        mapper.insert(
                id,
                new TenantMembershipRow(
                        membership.userId().value(),
                        membership.tenantId().value(),
                        membership.tenantDisplayName(),
                        membership.tenantLocale(),
                        toJson(membership.roleNames()),
                        toJson(membership.locationScopes()),
                        toJson(membership.partnerScopes())));
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("文字列リストの JSON 直列化に失敗: " + values, e);
        }
    }

    private TenantMembership toDomain(TenantMembershipRow row) {
        return new TenantMembership(
                new UserId(row.userId()),
                new TenantId(row.tenantId()),
                row.tenantDisplayName(),
                row.tenantLocale(),
                parseStringList(row.rolesJson()).stream().map(RoleName::new).toList(),
                parseStringList(row.locationScopesJson()),
                parseStringList(row.partnerScopesJson()));
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception e) {
            throw new IllegalStateException("JSONB の文字列リスト解釈に失敗: " + json, e);
        }
    }
}
