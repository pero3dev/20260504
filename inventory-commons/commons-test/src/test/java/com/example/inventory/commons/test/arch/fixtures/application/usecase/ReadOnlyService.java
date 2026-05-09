package com.example.inventory.commons.test.arch.fixtures.application.usecase;

import com.example.inventory.commons.test.arch.fixtures.application.port.out.FakeRepository;

/** 書込を呼ばない read-only fixture。 ルールは vacuously 合格させるべき({@code @Auditable} 不要)。 */
public class ReadOnlyService {

    private final FakeRepository repository;

    public ReadOnlyService(FakeRepository repository) {
        this.repository = repository;
    }

    public Object get(long id) {
        return repository.findById(id);
    }
}
