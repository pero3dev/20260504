package com.example.inventory.core.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.core.application.port.in.ReserveInventoryCommand;
import com.example.inventory.core.application.port.in.ReserveInventoryUseCase;
import com.example.inventory.core.application.port.in.UnknownSkuException;
import com.example.inventory.core.application.port.out.InventoryRepository;
import com.example.inventory.core.application.port.out.SkuRegistryPort;
import com.example.inventory.core.domain.model.Inventory;
import com.example.inventory.core.domain.model.InventoryId;
import com.example.inventory.core.domain.model.Quantity;
import com.example.inventory.core.domain.model.ReservationId;

/**
 * 在庫引当のユースケース。集約をロード → 変更 → 永続化 の順に処理する。 集約が発行したドメインイベントはリポジトリ実装が拾い、同一トランザクション内で {@code outbox}
 * テーブルへ書き込む(Transactional Outbox、ADR-0009)。
 *
 * <p>Master Data 連携(ADR-0004): Inventory.skuId は Master Data が管理する SKU を参照する。 Master Data は {@code
 * master.product.v1} を発行し、SkuMasterListener が Inventory Core の {@code sku_registry} 投影テーブルに upsert
 * する。引当時はこの投影で SKU 存在を検証し、 未登録の場合は {@link UnknownSkuException}(422) を返す。
 *
 * <p>横断関心事:
 *
 * <ul>
 *   <li>{@link Auditable} → AuditableAspect が {@code audit.log.v1} へ発行(ADR-0008)。
 *   <li>テナントスキーマへの {@code search_path} 切替は commons-tenant の {@code TenantSearchPathInterceptor}
 *       が担う(ADR-0003)。
 * </ul>
 */
@Service
public class ReserveInventoryService implements ReserveInventoryUseCase {

    private final InventoryRepository repository;
    private final SkuRegistryPort skuRegistry;
    private final SnowflakeIdGenerator idGenerator;

    public ReserveInventoryService(
            InventoryRepository repository,
            SkuRegistryPort skuRegistry,
            SnowflakeIdGenerator idGenerator) {
        this.repository = repository;
        this.skuRegistry = skuRegistry;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    @Auditable(
            action = "INVENTORY_RESERVE",
            targetType = "Inventory",
            targetIdExpression = "#command.inventoryId")
    public ReservationId reserve(ReserveInventoryCommand command) {
        Inventory inventory =
                repository
                        .findById(new InventoryId(command.inventoryId()))
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "在庫が見つかりません: " + command.inventoryId()));

        // SKU が Master Data 由来の投影テーブルに登録されているかを確認。
        // 投影遅延の可能性があるため 422(リトライ可)で返す。
        if (!skuRegistry.exists(inventory.skuId())) {
            throw new UnknownSkuException(inventory.skuId());
        }

        ReservationId reservationId = new ReservationId(idGenerator.nextId());
        inventory.reserve(reservationId, new Quantity(command.quantity()));
        repository.save(inventory);
        return reservationId;
    }
}
