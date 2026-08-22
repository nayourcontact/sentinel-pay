package com.acme.payments.adapter.out.provider;

import com.acme.payments.application.PaymentProvider;
import com.acme.payments.domain.Payment;
import org.springframework.stereotype.Component;

@Component
public class DeterministicMockPaymentProvider implements PaymentProvider {
    @Override
    public AuthorizationResult authorize(Payment payment) {
        int bucket = Math.floorMod(payment.merchantReference().hashCode(), 10);
        if (bucket == 0) return new Declined("provider_declined");
        return new Authorized("mock_" + payment.id().toString().replace("-", ""));
    }
}
