package com.acme.payments.adapter.out.persistence;

import com.acme.payments.application.Outbox;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.UUID;

@Repository
class JpaOutbox implements Outbox {
    private final OutboxJpaRepository repository;
    JpaOutbox(OutboxJpaRepository repository) { this.repository = repository; }

    @Override public void append(String aggregateType, UUID aggregateId, String eventType, String payload) {
        var e = new OutboxEventEntity();
        e.id = UUID.randomUUID();
        e.aggregateType = aggregateType;
        e.aggregateId = aggregateId;
        e.eventType = eventType;
        e.payload = payload;
        e.createdAt = Instant.now();
        e.attempts = 0;
        repository.save(e);
    }
}
