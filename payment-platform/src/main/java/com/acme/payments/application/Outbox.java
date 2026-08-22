package com.acme.payments.application;

import java.util.UUID;

public interface Outbox {
    void append(String aggregateType, UUID aggregateId, String eventType, String payload);
}
