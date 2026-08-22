package com.acme.payments.application;

import com.acme.payments.application.PaymentProvider.Authorized;
import com.acme.payments.application.PaymentProvider.Declined;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthorizationProcessor {
    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final Outbox outbox;

    public AuthorizationProcessor(PaymentRepository payments, PaymentProvider provider, Outbox outbox) {
        this.payments = payments;
        this.provider = provider;
        this.outbox = outbox;
    }

    @Transactional
    public void authorize(UUID paymentId) {
        var payment = payments.findById(paymentId).orElseThrow();
        if (payment.status() != com.acme.payments.domain.PaymentStatus.AUTHORIZATION_PENDING) return;

        var result = provider.authorize(payment);
        if (result instanceof Authorized success) {
            payment.markAuthorized(success.providerReference());
            payments.save(payment);
            outbox.append("Payment", payment.id(), "payment.authorized",
                    "{\"paymentId\":\"" + payment.id() + "\",\"providerReference\":\"" + success.providerReference() + "\"}");
        } else if (result instanceof Declined declined) {
            payment.markFailed(declined.reason());
            payments.save(payment);
            outbox.append("Payment", payment.id(), "payment.failed",
                    "{\"paymentId\":\"" + payment.id() + "\",\"reason\":\"" + escape(declined.reason()) + "\"}");
        }
    }

    private String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
