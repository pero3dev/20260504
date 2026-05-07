package com.example.inventory.workflow.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.commons.tenant.TenantId;
import com.example.inventory.workflow.domain.event.WorkflowInstanceCompletedEvent;

/**
 * WorkflowInstance 集約ルート。1 件の Saga 実行を表す(ADR-0015)。
 *
 * <p>純粋な POJO(ADR-0009)。MyBatis/JPA のアノテーション無し。 楽観ロック用 {@code version} を持ち、save 時は +1 される。
 *
 * <p>状態遷移は集約内のメソッドで完結:
 *
 * <ul>
 *   <li>{@link #start} で STARTED + step1=RUNNING、他 PENDING
 *   <li>{@link #completeCurrentStep} で current=COMPLETED、次があれば次=RUNNING、 無ければ全体=COMPLETED + イベント発行
 *   <li>{@link #failCurrentStep} で current=FAILED、全体=FAILED + イベント発行
 *   <li>{@link #cancel} で全体=CANCELLED + イベント発行(進行中ステップは FAILED 扱い)
 * </ul>
 *
 * <p>完了イベント({@link WorkflowInstanceCompletedEvent})は終端遷移時に 1 回だけ発行される(冪等性は呼出側 + DB トランザクションで担保)。
 */
public final class WorkflowInstance {

    private final WorkflowInstanceId id;
    private final TenantId tenantId;
    private final DefinitionKey definitionKey;
    private final String businessKey;
    private final String payloadJson;
    private final List<WorkflowStep> steps;
    private int currentStep;
    private WorkflowStatus status;
    private String error;
    private long version;
    private final Instant startedAt;
    private Instant completedAt;

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    public static WorkflowInstance restore(
            WorkflowInstanceId id,
            TenantId tenantId,
            DefinitionKey definitionKey,
            String businessKey,
            String payloadJson,
            List<WorkflowStep> steps,
            int currentStep,
            WorkflowStatus status,
            String error,
            long version,
            Instant startedAt,
            Instant completedAt) {
        return new WorkflowInstance(
                id,
                tenantId,
                definitionKey,
                businessKey,
                payloadJson,
                steps,
                currentStep,
                status,
                error,
                version,
                startedAt,
                completedAt);
    }

    /** 新規インスタンス開始。 stepNames は定義からスナップショットされる。 */
    public static WorkflowInstance start(
            WorkflowInstanceId id,
            TenantId tenantId,
            DefinitionKey definitionKey,
            String businessKey,
            String payloadJson,
            List<String> stepNames) {
        if (stepNames == null || stepNames.isEmpty())
            throw new IllegalArgumentException("stepNames は 1 つ以上必要");
        Instant now = Instant.now();
        List<WorkflowStep> steps = new ArrayList<>(stepNames.size());
        for (int i = 0; i < stepNames.size(); i++) {
            int stepNo = i + 1;
            WorkflowStep s = WorkflowStep.pending(stepNo, stepNames.get(i));
            steps.add(stepNo == 1 ? s.markRunning(now) : s);
        }
        return new WorkflowInstance(
                id,
                tenantId,
                definitionKey,
                businessKey,
                payloadJson,
                steps,
                1,
                WorkflowStatus.STARTED,
                null,
                0L,
                now,
                null);
    }

    private WorkflowInstance(
            WorkflowInstanceId id,
            TenantId tenantId,
            DefinitionKey definitionKey,
            String businessKey,
            String payloadJson,
            List<WorkflowStep> steps,
            int currentStep,
            WorkflowStatus status,
            String error,
            long version,
            Instant startedAt,
            Instant completedAt) {
        if (id == null) throw new IllegalArgumentException("id は必須");
        if (tenantId == null) throw new IllegalArgumentException("tenantId は必須");
        if (definitionKey == null) throw new IllegalArgumentException("definitionKey は必須");
        if (steps == null || steps.isEmpty()) throw new IllegalArgumentException("steps は 1 つ以上必要");
        this.id = id;
        this.tenantId = tenantId;
        this.definitionKey = definitionKey;
        this.businessKey = businessKey;
        this.payloadJson = payloadJson == null ? "{}" : payloadJson;
        this.steps = new ArrayList<>(steps);
        this.currentStep = currentStep;
        this.status = status;
        this.error = error;
        this.version = version;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    /** 現在ステップを COMPLETED に。次があれば RUNNING へ進める、無ければ全体を COMPLETED + イベント発行。 */
    public void completeCurrentStep() {
        ensureStarted();
        Instant now = Instant.now();
        WorkflowStep current = stepAt(currentStep);
        if (current.status() != StepStatus.RUNNING) {
            throw new IllegalStateException("現ステップが RUNNING ではない: " + current.status());
        }
        replaceStep(current.markCompleted(now));
        if (currentStep < steps.size()) {
            currentStep++;
            replaceStep(stepAt(currentStep).markRunning(now));
        } else {
            this.status = WorkflowStatus.COMPLETED;
            this.completedAt = now;
            emitCompleted(now);
        }
    }

    /** 現在ステップを FAILED に。全体も FAILED にしてイベント発行。 */
    public void failCurrentStep(String reason) {
        ensureStarted();
        Instant now = Instant.now();
        WorkflowStep current = stepAt(currentStep);
        if (current.status() != StepStatus.RUNNING) {
            throw new IllegalStateException("現ステップが RUNNING ではない: " + current.status());
        }
        replaceStep(current.markFailed(now, reason));
        this.status = WorkflowStatus.FAILED;
        this.error = reason;
        this.completedAt = now;
        emitCompleted(now);
    }

    /**
     * インスタンス全体の SLA(definition.instanceSla)を超過していれば現ステップを FAILED に遷移させて全体も FAILED にし、 {@link
     * WorkflowInstanceCompletedEvent} を発行する。 ADR-0015 で例示した「中央タイマ」の実体(B2)。
     *
     * <ul>
     *   <li>{@code sla} が ZERO ならば SLA 無効、 何もしない(冪等)
     *   <li>STARTED 以外の状態(既に終端済)は何もしない(冪等)
     *   <li>{@code now - startedAt < sla} ならばまだ余裕あり、 何もしない
     * </ul>
     *
     * @return SLA 超過で状態遷移したら true、 そうでなければ false
     */
    public boolean expireIfOverdue(Duration sla, Instant now) {
        if (sla == null || sla.isZero() || sla.isNegative()) {
            return false;
        }
        if (status != WorkflowStatus.STARTED) {
            return false;
        }
        Instant deadline = startedAt.plus(sla);
        if (!now.isAfter(deadline)) {
            return false;
        }
        WorkflowStep current = stepAt(currentStep);
        if (current.status() == StepStatus.RUNNING) {
            replaceStep(current.markFailed(now, "SLA timeout"));
        }
        this.status = WorkflowStatus.FAILED;
        this.error = "SLA timeout (deadline=" + deadline + ")";
        this.completedAt = now;
        emitCompleted(now);
        return true;
    }

    /** 進行中インスタンスを CANCELLED にする。RUNNING 中のステップは FAILED として閉じる。 */
    public void cancel(String reason) {
        if (status == WorkflowStatus.CANCELLED) return; // 冪等
        ensureStarted();
        Instant now = Instant.now();
        WorkflowStep current = stepAt(currentStep);
        if (current.status() == StepStatus.RUNNING) {
            replaceStep(current.markFailed(now, "cancelled: " + reason));
        }
        this.status = WorkflowStatus.CANCELLED;
        this.error = reason;
        this.completedAt = now;
        emitCompleted(now);
    }

    private void ensureStarted() {
        if (status != WorkflowStatus.STARTED) {
            throw new IllegalStateException("STARTED 状態のインスタンスのみ進行可能。現状態=" + status);
        }
    }

    private WorkflowStep stepAt(int stepNo) {
        return steps.get(stepNo - 1);
    }

    private void replaceStep(WorkflowStep updated) {
        steps.set(updated.stepNo() - 1, updated);
    }

    private void emitCompleted(Instant occurredAt) {
        pendingEvents.add(
                new WorkflowInstanceCompletedEvent(
                        id.value(),
                        definitionKey.name(),
                        businessKey,
                        status,
                        error,
                        completedAt,
                        occurredAt));
    }

    public WorkflowInstanceId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public DefinitionKey definitionKey() {
        return definitionKey;
    }

    public String businessKey() {
        return businessKey;
    }

    public String payloadJson() {
        return payloadJson;
    }

    public List<WorkflowStep> steps() {
        return Collections.unmodifiableList(steps);
    }

    public int currentStep() {
        return currentStep;
    }

    public int totalSteps() {
        return steps.size();
    }

    public WorkflowStatus status() {
        return status;
    }

    public String error() {
        return error;
    }

    public long version() {
        return version;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public List<DomainEvent> pendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    public void clearPendingEvents() {
        pendingEvents.clear();
    }
}
