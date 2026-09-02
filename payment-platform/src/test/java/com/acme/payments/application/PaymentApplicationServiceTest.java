package com.acme.payments.application;

import com.acme.payments.domain.Money;
import com.acme.payments.domain.Payment;
import com.acme.payments.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentApplicationService")
class PaymentApplicationServiceTest {

    @Mock
    private PaymentRepository payments;

    @Mock
    private Outbox outbox;

    private PaymentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentApplicationService(payments, outbox);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void shouldCreatePendingPaymentAndAppendAuthorizationEvent() {
            PaymentApplicationService.CreatePayment command =
                    new PaymentApplicationService.CreatePayment(
                            "order-2026-001",
                            "149.90",
                            "EUR"
                    );

            when(payments.findByMerchantReference("order-2026-001"))
                    .thenReturn(Optional.empty());

            when(payments.save(any(Payment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Payment result = service.create(command);

            assertThat(result.id()).isNotNull();
            assertThat(result.merchantReference()).isEqualTo("order-2026-001");
            assertThat(result.money()).isEqualTo(Money.of("149.90", "EUR"));
            assertThat(result.status())
                    .isEqualTo(PaymentStatus.AUTHORIZATION_PENDING);

            ArgumentCaptor<Payment> paymentCaptor =
                    ArgumentCaptor.forClass(Payment.class);

            verify(payments).save(paymentCaptor.capture());

            Payment savedPayment = paymentCaptor.getValue();

            assertThat(savedPayment.status())
                    .isEqualTo(PaymentStatus.AUTHORIZATION_PENDING);

            verify(outbox).append(
                    "Payment",
                    result.id(),
                    "payment.authorization.requested",
                    "{\"paymentId\":\"" + result.id() + "\"}"
            );

            verifyNoMoreInteractions(outbox);
        }

        @Test
        void shouldReturnExistingPaymentForSameMerchantReference() {
            Payment existing = Payment.create(
                    "order-2026-001",
                    Money.of("149.90", "EUR")
            );
            existing.requestAuthorization();

            when(payments.findByMerchantReference("order-2026-001"))
                    .thenReturn(Optional.of(existing));

            PaymentApplicationService.CreatePayment command =
                    new PaymentApplicationService.CreatePayment(
                            "order-2026-001",
                            "9999.99",
                            "USD"
                    );

            Payment result = service.create(command);

            assertThat(result).isSameAs(existing);

            verify(payments).findByMerchantReference("order-2026-001");
            verify(payments, never()).save(any());
            verifyNoInteractions(outbox);
        }

        @Test
        void shouldNotAppendEventWhenSavingPaymentFails() {
            PaymentApplicationService.CreatePayment command =
                    new PaymentApplicationService.CreatePayment(
                            "order-save-failure",
                            "10.00",
                            "EUR"
                    );

            when(payments.findByMerchantReference("order-save-failure"))
                    .thenReturn(Optional.empty());

            when(payments.save(any(Payment.class)))
                    .thenThrow(new RuntimeException("database unavailable"));

            assertThatThrownBy(() -> service.create(command))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("database unavailable");

            verifyNoInteractions(outbox);
        }

        @Test
        void shouldRejectInvalidMoneyBeforeSavingPayment() {
            PaymentApplicationService.CreatePayment command =
                    new PaymentApplicationService.CreatePayment(
                            "order-invalid",
                            "-10.00",
                            "EUR"
                    );

            when(payments.findByMerchantReference("order-invalid"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(command))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(payments, never()).save(any());
            verifyNoInteractions(outbox);
        }
    }

    @Nested
    @DisplayName("get")
    class Get {

        @Test
        void shouldReturnExistingPayment() {
            UUID id = UUID.randomUUID();

            Payment existing = Payment.create(
                    "order-get",
                    Money.of("10.00", "EUR")
            );

            when(payments.findById(id))
                    .thenReturn(Optional.of(existing));

            Payment result = service.get(id);

            assertThat(result).isSameAs(existing);

            verify(payments).findById(id);
        }

        @Test
        void shouldThrowPaymentNotFoundExceptionWhenPaymentDoesNotExist() {
            UUID id = UUID.randomUUID();

            when(payments.findById(id))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.get(id))
                    .isInstanceOf(
                            PaymentApplicationService.PaymentNotFoundException.class
                    )
                    .hasMessage("payment not found: " + id);

            verify(payments).findById(id);
        }
    }
}