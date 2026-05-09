package com.example.inventory.commons.test.arch.fixtures.application.usecase;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.test.arch.fixtures.application.port.out.FakeRepository;

/** 書込を呼び、 公開メソッドに {@code @Auditable} を付与した compliant fixture。 */
public class GoodAuditableService {

    private final FakeRepository repository;

    public GoodAuditableService(FakeRepository repository) {
        this.repository = repository;
    }

    @Auditable(action = "FAKE_SAVE", targetType = "Fake")
    public void save(Object aggregate) {
        repository.save(aggregate);
    }
}
