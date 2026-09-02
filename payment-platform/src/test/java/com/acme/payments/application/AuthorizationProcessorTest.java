package com.acme.payments.application;

import com.acme.payments.domain.Money;
import com.acme.payments.domain.Payment;
import com.acme.payments.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthorizationProcessor")
class AuthorizationProcessorTest {

    @Mock
    private PaymentRepository payments;

    @Mock
    private PaymentProvider provider;

    @Mock
    private Outbox outbox;

    private AuthorizationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AuthorizationProcessor(
                payments,
                provider,
                outbox
        );
    }

    @Nested
    @DisplayName("successful authorization")
    class SuccessfulAuthorization {

        @Test
        void shouldAuthorizePendingPayment() {
            Payment payment = pendingPayment();
            UUID paymentId = payment.id();

            when(payments.findById(paymentId))
                    .thenReturn(Optional.of(payment));

            when(provider.authorize(payment))
                    .thenReturn(
                            new PaymentProvider.Authorized("provider-123")
                    );

            processor.authorize(paymentId);

            assertThat(payment.status())
                    .isEqualTo(PaymentStatus.AUTHORIZED);

            assertThat(payment.providerReference())
                    .isEqualTo("provider-123");

            assertThat(payment.failureReason()).isNull();

            verify(payments).save(payment);

            verify(outbox).append(
                    "Payment",
                    paymentId,
                    "payment.authorized",
                    "{\"paymentId\":\"" + paymentId
                            + "\",\"providerReference\":\"provider-123\"}"
            );
        }

        @Test
        void shouldInvokeProviderBeforePersistingAndPublishingResult() {
            Payment payment = pendingPayment();

            when(payments.findById(payment.id()))
                    .thenReturn(Optional.of(payment));

            when(provider.authorize(payment))
                    .thenReturn(
                            new PaymentProvider.Authorized("provider-1")
                    );

            processor.authorize(payment.id());

            InOrder inOrder = inOrder(provider, payments, outbox);

            inOrder.verify(provider).authorize(payment);
            inOrder.verify(payments).save(payment);
            inOrder.verify(outbox).append(
                    eq("Payment"),
                    eq(payment.id()),
                    eq("payment.authorized"),
                    anyString()
            );
        }
    }

    @Nested
    @DisplayName("declined authorization")
    class DeclinedAuthorization {

        @Test
        void shouldMarkPaymentFailedWhenProviderDeclines() {
            Payment payment = pendingPayment();

            when(payments.findById(payment.id()))
                    .thenReturn(Optional.of(payment));

            when(provider.authorize(payment))
                    .thenReturn(
                            new PaymentProvider.Declined("insufficient funds")
                    );

            processor.authorize(payment.id());

            assertThat(payment.status())
                    .isEqualTo(PaymentStatus.FAILED);

            assertThat(payment.failureReason())
                    .isEqualTo("insufficient funds");

            verify(payments).save(payment);

            verify(outbox).append(
                    "Payment",
                    payment.id(),
                    "payment.failed",
                    "{\"paymentId\":\"" + payment.id()
                            + "\",\"reason\":\"insufficient funds\"}"
            );
        }

        @Test
        void shouldEscapeQuotesAndBackslashesInFailureEvent() {
            Payment payment = pendingPayment();

            when(payments.findById(payment.id()))
                    .thenReturn(Optional.of(payment));

            when(provider.authorize(payment))
                    .thenReturn(
                            new PaymentProvider.Declined(
                                    "provider \"timeout\" at C:\\gateway"
                            )
                    );

            processor.authorize(payment.id());

            verify(outbox).append(
                    "Payment",
                    payment.id(),
                    "payment.failed",
                    "{\"paymentId\":\"" + payment.id()
                            + "\",\"reason\":\"provider \\\"timeout\\\" at C:\\\\gateway\"}"
            );
        }
    }

    @Nested
    @DisplayName("idempotency")
    class Idempotency {

        @Test
        void shouldIgnoreAlreadyAuthorizedPayment() {
            Payment payment = pendingPayment();
            payment.markAuthorized("provider-123");

            when(payments.findById(payment.id()))
                    .thenReturn(Optional.of(payment));

            processor.authorize(payment.id());

            verifyNoInteractions(provider);
            verify(payments, never()).save(any());
            verifyNoInteractions(outbox);
        }

        @Test
        void shouldIgnoreFailedPayment() {
            Payment payment = pendingPayment();
            payment.markFailed("declined");

            when(payments.findById(payment.id()))
                    .thenReturn(Optional.of(payment));

            processor.authorize(payment.id());

            verifyNoInteractions(provider);
            verify(payments, never()).save(any());
            verifyNoInteractions(outbox);
        }

        @Test
        void shouldIgnoreCancelledPayment() {
            Payment payment = pendingPayment();
            payment.cancel();

            when(payments.findById(payment.id()))
                    .thenReturn(Optional.of(payment));

            processor.authorize(payment.id());

            verifyNoInteractions(provider);
            verify(payments, never()).save(any());
            verifyNoInteractions(outbox);
        }
    }

    @Nested
    @DisplayName("failure handling")
    class FailureHandling {

        @Test
        void shouldFailWhenPaymentDoesNotExist() {
            UUID paymentId = UUID.randomUUID();

            when(payments.findById(paymentId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                    () -> processor.authorize(paymentId)
            ).isInstanceOf(NoSuchElementException.class);

            verifyNoInteractions(provider);
            verifyNoInteractions(outbox);
        }

        @Test
        void shouldNotSaveOrPublishWhenProviderThrows() {
            Payment payment = pendingPayment();

            when(payments.findById(payment.id()))
                    .thenReturn(Optional.of(payment));

            when(provider.authorize(payment))
                    .thenThrow(new RuntimeException("provider unavailable"));

            assertThatThrownBy(
                    () -> processor.authorize(payment.id())
            )
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("provider unavailable");

            assertThat(payment.status())
                    .isEqualTo(PaymentStatus.AUTHORIZATION_PENDING);

            verify(payments, never()).save(any());
            verifyNoInteractions(outbox);
        }
    }

    private Payment pendingPayment() {
        Payment payment = Payment.create(
                "order-42",
                Money.of("149.90", "EUR")
        );
        payment.requestAuthorization();
        return payment;
    }
}