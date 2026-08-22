package com.acme.payments.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
class OutboxEventEntity {
    @Id UUID id;
    @Column(nullable = false) String aggregateType;
    @Column(nullable = false) UUID aggregateId;
    @Column(nullable = false) String eventType;
    @Column(nullable = false, columnDefinition = "text") String payload;
    @Column(nullable = false) Instant createdAt;
    Instant publishedAt;
    Instant failedAt;
    @Column(columnDefinition = "text") String lastError;
    @Column(nullable = false) int attempts;
}
