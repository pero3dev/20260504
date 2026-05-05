package com.example.inventory.core.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.core.application.port.in.RegisterSkuFromMasterUseCase;
import com.example.inventory.core.application.port.out.SkuRegistryPort;
import com.example.inventory.core.domain.model.SkuId;
import com.example.inventory.core.domain.model.SkuRegistration;

/**
 * {@code master.product.v1} を受信した際の SKU 投影更新サービス。
 *
 * <p>冪等性は upsert 側で {@code version} 比較を行うため、再配信されても古い値で 上書きされない(投影専用テーブル & ON CONFLICT DO UPDATE
 * WHERE excluded.version > sku_registry.version)。
 *
 * <p>{@code @Auditable} は付けない。投影更新はシステム駆動で操作者の概念が無く、 監査の権威は Master Data 側 SKU_CREATE で既に取得済みのため。
 */
@Service
public class RegisterSkuFromMasterService implements RegisterSkuFromMasterUseCase {

    private final SkuRegistryPort registryPort;

    public RegisterSkuFromMasterService(SkuRegistryPort registryPort) {
        this.registryPort = registryPort;
    }

    @Override
    @Transactional
    public void register(Command command) {
        registryPort.upsert(
                new SkuRegistration(
                        new SkuId(command.code()),
                        command.aggregateId(),
                        command.name(),
                        command.unitOfMeasure() == null ? "" : command.unitOfMeasure(),
                        command.versionAfter()));
    }
}
