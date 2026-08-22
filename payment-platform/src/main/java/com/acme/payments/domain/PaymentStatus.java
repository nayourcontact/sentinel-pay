package com.acme.payments.domain;

public enum PaymentStatus {
    CREATED,
    AUTHORIZATION_PENDING,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    CANCELLED
}
