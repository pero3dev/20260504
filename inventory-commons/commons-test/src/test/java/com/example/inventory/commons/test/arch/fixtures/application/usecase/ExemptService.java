package com.example.inventory.commons.test.arch.fixtures.application.usecase;

import com.example.inventory.commons.audit.AuditExempt;
import com.example.inventory.commons.test.arch.fixtures.application.port.out.FakeRepository;

/**
 * 書込を呼ぶが、 正当な理由で {@code @Auditable} の代わりに {@code @AuditExempt} を 付けた fixture。 ルールはこのクラスを 合格 させるべき
 * (audit emitter / projection の正当な exempt を許可)。
 */
public class ExemptService {

    private final FakeRepository repository;

    public ExemptService(FakeRepository repository) {
        this.repository = repository;
    }

    @AuditExempt(reason = "synthetic fixture for rule self-test")
    public void save(Object aggregate) {
        repository.save(aggregate);
    }
}
