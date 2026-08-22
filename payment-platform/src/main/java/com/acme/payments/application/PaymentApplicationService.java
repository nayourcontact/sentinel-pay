package com.acme.payments.application;

import com.acme.payments.domain.Money;
import com.acme.payments.domain.Payment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentApplicationService {
    private final PaymentRepository payments;
    private final Outbox outbox;

    public PaymentApplicationService(PaymentRepository payments, Outbox outbox) {
        this.payments = payments;
        this.outbox = outbox;
    }

    @Transactional
    public Payment create(CreatePayment command) {
        var existing = payments.findByMerchantReference(command.merchantReference());
        if (existing.isPresent()) return existing.get();

        var payment = Payment.create(command.merchantReference(), Money.of(command.amount(), command.currency()));
        payment.requestAuthorization();
        var saved = payments.save(payment);
        outbox.append("Payment", saved.id(), "payment.authorization.requested",
                "{\"paymentId\":\"" + saved.id() + "\"}");
        return saved;
    }

    @Transactional(readOnly = true)
    public Payment get(UUID id) {
        return payments.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    public record CreatePayment(String merchantReference, String amount, String currency) {}

    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(UUID id) { super("payment not found: " + id); }
    }
}
