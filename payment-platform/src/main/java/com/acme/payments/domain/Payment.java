package com.acme.payments.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Payment {
    private final UUID id;
    private final String merchantReference;
    private final Money money;
    private PaymentStatus status;
    private String providerReference;
    private String failureReason;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private Payment(UUID id, String merchantReference, Money money, PaymentStatus status,
                    String providerReference, String failureReason, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.merchantReference = Objects.requireNonNull(merchantReference);
        this.money = Objects.requireNonNull(money);
        this.status = Objects.requireNonNull(status);
        this.providerReference = providerReference;
        this.failureReason = failureReason;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
    }

    public static Payment create(String merchantReference, Money money) {
        var now = Instant.now();
        return new Payment(UUID.randomUUID(), merchantReference, money, PaymentStatus.CREATED, null, null, now, now, 0);
    }

    public static Payment rehydrate(UUID id, String merchantReference, Money money, PaymentStatus status,
                                    String providerReference, String failureReason, Instant createdAt, Instant updatedAt, long version) {
        return new Payment(id, merchantReference, money, status, providerReference, failureReason, createdAt, updatedAt, version);
    }

    public void requestAuthorization() {
        requireStatus(PaymentStatus.CREATED);
        status = PaymentStatus.AUTHORIZATION_PENDING;
        updatedAt = Instant.now();
    }

    public void markAuthorized(String providerReference) {
        requireStatus(PaymentStatus.AUTHORIZATION_PENDING);
        this.status = PaymentStatus.AUTHORIZED;
        this.providerReference = Objects.requireNonNull(providerReference);
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        if (status != PaymentStatus.AUTHORIZATION_PENDING && status != PaymentStatus.CREATED) {
            throw new IllegalStateException("payment cannot fail from status " + status);
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = Objects.requireNonNull(reason);
        this.updatedAt = Instant.now();
    }

    public void capture() {
        requireStatus(PaymentStatus.AUTHORIZED);
        this.status = PaymentStatus.CAPTURED;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (status == PaymentStatus.CAPTURED) throw new IllegalStateException("captured payment cannot be cancelled");
        if (status == PaymentStatus.CANCELLED) return;
        this.status = PaymentStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    private void requireStatus(PaymentStatus expected) {
        if (status != expected) throw new IllegalStateException("expected status " + expected + " but was " + status);
    }

    public UUID id() { return id; }
    public String merchantReference() { return merchantReference; }
    public Money money() { return money; }
    public PaymentStatus status() { return status; }
    public String providerReference() { return providerReference; }
    public String failureReason() { return failureReason; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
