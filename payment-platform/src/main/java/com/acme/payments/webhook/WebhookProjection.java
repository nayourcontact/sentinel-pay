package com.acme.payments.webhook;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class WebhookProjection {
    private final WebhookRepository repository;
    public WebhookProjection(WebhookRepository repository) { this.repository = repository; }

    @Transactional
    @KafkaListener(topics = {"payment.authorized", "payment.failed"}, groupId = "webhook-projection")
    public void enqueue(ConsumerRecord<String, String> record) {
        String eventId = record.topic() + ":" + record.partition() + ":" + record.offset();
        if (repository.existsByEventId(eventId)) return;
        var e = new WebhookDeliveryEntity();
        e.id = UUID.randomUUID();
        e.eventId = eventId;
        e.eventType = record.topic();
        e.payload = record.value();
        e.status = "PENDING";
        e.attempts = 0;
        e.nextAttemptAt = Instant.now();
        e.createdAt = Instant.now();
        repository.save(e);
    }
}
