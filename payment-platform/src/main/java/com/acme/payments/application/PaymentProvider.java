package com.acme.payments.application;

import com.acme.payments.domain.Payment;

public interface PaymentProvider {
    AuthorizationResult authorize(Payment payment);

    sealed interface AuthorizationResult permits Authorized, Declined {}
    record Authorized(String providerReference) implements AuthorizationResult {}
    record Declined(String reason) implements AuthorizationResult {}
}
