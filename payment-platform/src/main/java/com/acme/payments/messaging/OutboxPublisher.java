package com.acme.payments.messaging;

import com.acme.payments.adapter.out.persistence.OutboxPublishingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
    private final OutboxPublishingService service;
    public OutboxPublisher(OutboxPublishingService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${payments.outbox.poll-delay:250}")
    public void publish() { service.publishBatch(); }
}
