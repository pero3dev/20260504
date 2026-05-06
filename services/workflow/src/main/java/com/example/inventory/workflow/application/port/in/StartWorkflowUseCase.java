package com.example.inventory.workflow.application.port.in;

import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.workflow.domain.model.DefinitionKey;
import com.example.inventory.workflow.domain.model.WorkflowInstance;

/**
 * 新しい Workflow インスタンスを開始するユースケース。
 *
 * <p>定義キー({@link DefinitionKey})からステップ列をスナップショットし、 status=STARTED / current_step=1 / step1=RUNNING
 * の初期状態で永続化する。
 */
public interface StartWorkflowUseCase {

    WorkflowInstance start(Command command);

    record Command(
            TenantId tenantId,
            DefinitionKey definitionKey,
            String businessKey,
            String payloadJson) {

        public Command {
            if (tenantId == null) throw new IllegalArgumentException("tenantId は必須");
            if (definitionKey == null) throw new IllegalArgumentException("definitionKey は必須");
        }
    }
}
