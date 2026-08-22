package com.acme.payments.webhook;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries", uniqueConstraints = @UniqueConstraint(name = "uk_webhook_event", columnNames = {"eventId"}))
class WebhookDeliveryEntity {
    @Id UUID id;
    @Column(nullable = false) String eventId;
    @Column(nullable = false) String eventType;
    @Column(nullable = false, columnDefinition = "text") String payload;
    @Column(nullable = false) String status;
    @Column(nullable = false) int attempts;
    @Column(nullable = false) Instant nextAttemptAt;
    @Column(nullable = false) Instant createdAt;
}
