package com.example.inventory.commons.test.arch.fixtures.application.usecase;

import com.example.inventory.commons.test.arch.fixtures.application.port.out.FakeRepository;

/** 書込を呼ぶが {@code @Auditable} を 1 つも付けていない non-compliant fixture。 ルールはこのクラスを違反として 検出 すべき。 */
public class BadNonAuditableService {

    private final FakeRepository repository;

    public BadNonAuditableService(FakeRepository repository) {
        this.repository = repository;
    }

    public void save(Object aggregate) {
        repository.save(aggregate);
    }
}
