package com.example.inventory.identity.application.port.out;

import java.util.List;
import java.util.Optional;

import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.identity.domain.model.Tenant;

/** Tenant 集約の永続化(A5、 Pool 方式)。 */
public interface TenantRepository {

    /**
     * 新規 append。 同 tenantId が既存ならば実装は {@link org.springframework.dao.DuplicateKeyException}
     * を投げる(use case 側で 409 に変換)。
     */
    void append(Tenant tenant);

    /** 既存 update(deactivate 用)。 該当無しなら {@code 0} 行更新で正常終了。 */
    void update(Tenant tenant);

    Optional<Tenant> findById(TenantId tenantId);

    List<Tenant> findAll();
}
