package com.acme.payments.application;

import com.acme.payments.domain.Payment;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(UUID id);
    Optional<Payment> findByMerchantReference(String merchantReference);
}
