package com.acme.payments.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

interface WebhookRepository extends JpaRepository<WebhookDeliveryEntity, UUID> {
    boolean existsByEventId(String eventId);
}
