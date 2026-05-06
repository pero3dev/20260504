package com.example.inventory.manufacturing.adapter.out.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.inventory.commons.event.OutboxRecord;
import com.example.inventory.commons.event.OutboxRepository;

@Repository
public class OutboxRepositoryImpl implements OutboxRepository {

    private final OutboxMapper mapper;

    public OutboxRepositoryImpl(OutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void append(OutboxRecord record) {
        mapper.insert(record);
    }

    @Override
    public List<OutboxRecord> pickUnpublished(int batchSize) {
        return mapper.pickUnpublished(batchSize);
    }

    @Override
    public void markPublished(long eventId) {
        mapper.markPublished(eventId);
    }
}
