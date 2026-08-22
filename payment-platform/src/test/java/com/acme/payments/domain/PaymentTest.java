package com.acme.payments.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaymentTest {
    @Test void authorization_state_machine_is_enforced() {
        var payment = Payment.create("order-42", Money.of("42.00", "EUR"));
        payment.requestAuthorization();
        payment.markAuthorized("provider-1");
        assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThatThrownBy(() -> payment.markAuthorized("provider-2")).isInstanceOf(IllegalStateException.class);
    }

    @Test void captured_payment_cannot_be_cancelled() {
        var payment = Payment.create("order-43", Money.of("10.00", "EUR"));
        payment.requestAuthorization();
        payment.markAuthorized("provider-2");
        payment.capture();
        assertThatThrownBy(payment::cancel).isInstanceOf(IllegalStateException.class);
    }
}
