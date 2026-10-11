package com.acme.payments.application;

import com.acme.payments.domain.Payment;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    /** Serialize create attempts for the same merchant reference within the active transaction. */
    void lockMerchantReference(String merchantReference);
    Payment save(Payment payment);
    Optional<Payment> findById(UUID id);
    Optional<Payment> findByMerchantReference(String merchantReference);
}
