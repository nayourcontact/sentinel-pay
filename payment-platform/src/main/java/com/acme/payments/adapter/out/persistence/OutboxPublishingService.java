package com.acme.payments.adapter.out.persistence;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OutboxPublishingService {
    private final OutboxJpaRepository repository;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublishingService(OutboxJpaRepository repository, KafkaTemplate<String, String> kafka) {
        this.repository = repository;
        this.kafka = kafka;
    }

    @Transactional
    public void publishBatch() {
        for (var event : repository.lockNextBatch(100)) {
            try {
                kafka.send(event.eventType, event.aggregateId.toString(), event.payload).get();
                event.publishedAt = Instant.now();
            } catch (Exception ex) {
                event.attempts++;
                event.lastError = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
                if (event.attempts >= 10) {
                    event.failedAt = Instant.now(); // durable quarantine: never masquerade a failed event as published
                }
            }
        }
    }
}
