package com.acme.payments.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Payment")
class PaymentTest {

    private static final Money DEFAULT_MONEY =
            Money.of("149.90", "EUR");

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        void shouldCreatePaymentInCreatedState() {
            Payment payment = Payment.create(
                    "order-42",
                    DEFAULT_MONEY
            );

            assertThat(payment.id()).isNotNull();
            assertThat(payment.merchantReference()).isEqualTo("order-42");
            assertThat(payment.money()).isEqualTo(DEFAULT_MONEY);
            assertThat(payment.status()).isEqualTo(PaymentStatus.CREATED);

            assertThat(payment.providerReference()).isNull();
            assertThat(payment.failureReason()).isNull();

            assertThat(payment.createdAt()).isNotNull();
            assertThat(payment.updatedAt()).isEqualTo(payment.createdAt());

            assertThat(payment.version()).isZero();
        }

        @Test
        void shouldGenerateDifferentIdsForDifferentPayments() {
            Payment first = Payment.create("order-1", DEFAULT_MONEY);
            Payment second = Payment.create("order-2", DEFAULT_MONEY);

            assertThat(first.id()).isNotEqualTo(second.id());
        }

        @Test
        void shouldRejectNullMerchantReference() {
            assertThatThrownBy(
                    () -> Payment.create(null, DEFAULT_MONEY)
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectNullMoney() {
            assertThatThrownBy(
                    () -> Payment.create("order-42", null)
            ).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("rehydration")
    class Rehydration {

        @Test
        void shouldRestoreCompletePersistedState() {
            UUID id = UUID.randomUUID();
            Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
            Instant updatedAt = Instant.parse("2026-01-01T11:00:00Z");

            Payment payment = Payment.rehydrate(
                    id,
                    "order-99",
                    DEFAULT_MONEY,
                    PaymentStatus.AUTHORIZED,
                    "provider-123",
                    null,
                    createdAt,
                    updatedAt,
                    7
            );

            assertThat(payment.id()).isEqualTo(id);
            assertThat(payment.merchantReference()).isEqualTo("order-99");
            assertThat(payment.money()).isEqualTo(DEFAULT_MONEY);
            assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
            assertThat(payment.providerReference()).isEqualTo("provider-123");
            assertThat(payment.failureReason()).isNull();
            assertThat(payment.createdAt()).isEqualTo(createdAt);
            assertThat(payment.updatedAt()).isEqualTo(updatedAt);
            assertThat(payment.version()).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("authorization request")
    class AuthorizationRequest {

        @Test
        void shouldMoveCreatedPaymentToAuthorizationPending() {
            Payment payment = newPayment();
            Instant previousUpdatedAt = payment.updatedAt();

            payment.requestAuthorization();

            assertThat(payment.status())
                    .isEqualTo(PaymentStatus.AUTHORIZATION_PENDING);

            assertThat(payment.updatedAt())
                    .isAfterOrEqualTo(previousUpdatedAt);
        }

        @Test
        void shouldRejectRepeatedAuthorizationRequest() {
            Payment payment = pendingPayment();

            assertThatThrownBy(payment::requestAuthorization)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("expected status CREATED")
                    .hasMessageContaining("AUTHORIZATION_PENDING");
        }
    }

    @Nested
    @DisplayName("successful authorization")
    class Authorization {

        @Test
        void shouldAuthorizePendingPayment() {
            Payment payment = pendingPayment();

            payment.markAuthorized("provider-ref-1");

            assertThat(payment.status())
                    .isEqualTo(PaymentStatus.AUTHORIZED);

            assertThat(payment.providerReference())
                    .isEqualTo("provider-ref-1");

            assertThat(payment.failureReason()).isNull();
        }

        @Test
        void shouldRejectAuthorizationFromCreatedState() {
            Payment payment = newPayment();

            assertThatThrownBy(
                    () -> payment.markAuthorized("provider-ref-1")
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AUTHORIZATION_PENDING")
                    .hasMessageContaining("CREATED");
        }

        @Test
        void shouldRejectRepeatedAuthorization() {
            Payment payment = authorizedPayment();

            assertThatThrownBy(
                    () -> payment.markAuthorized("provider-ref-2")
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AUTHORIZATION_PENDING")
                    .hasMessageContaining("AUTHORIZED");
        }
    }

    @Nested
    @DisplayName("failure")
    class Failure {

        @Test
        void shouldFailCreatedPayment() {
            Payment payment = newPayment();

            payment.markFailed("provider unavailable");

            assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.failureReason())
                    .isEqualTo("provider unavailable");
        }

        @Test
        void shouldFailPendingPayment() {
            Payment payment = pendingPayment();

            payment.markFailed("card declined");

            assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.failureReason()).isEqualTo("card declined");
        }

        @Test
        void shouldRejectFailureFromAuthorizedState() {
            Payment payment = authorizedPayment();

            assertThatThrownBy(
                    () -> payment.markFailed("late failure")
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("payment cannot fail")
                    .hasMessageContaining("AUTHORIZED");
        }

        @Test
        void shouldRejectFailureFromCapturedState() {
            Payment payment = capturedPayment();

            assertThatThrownBy(
                    () -> payment.markFailed("late failure")
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CAPTURED");
        }
    }

    @Nested
    @DisplayName("capture")
    class Capture {

        @Test
        void shouldCaptureAuthorizedPayment() {
            Payment payment = authorizedPayment();

            payment.capture();

            assertThat(payment.status())
                    .isEqualTo(PaymentStatus.CAPTURED);
        }

        @Test
        void shouldRejectCaptureFromCreatedState() {
            Payment payment = newPayment();

            assertThatThrownBy(payment::capture)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AUTHORIZED")
                    .hasMessageContaining("CREATED");
        }

        @Test
        void shouldRejectCaptureFromPendingState() {
            Payment payment = pendingPayment();

            assertThatThrownBy(payment::capture)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AUTHORIZED")
                    .hasMessageContaining("AUTHORIZATION_PENDING");
        }

        @Test
        void shouldRejectRepeatedCapture() {
            Payment payment = capturedPayment();

            assertThatThrownBy(payment::capture)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AUTHORIZED")
                    .hasMessageContaining("CAPTURED");
        }
    }

    @Nested
    @DisplayName("cancellation")
    class Cancellation {

        @Test
        void shouldCancelCreatedPayment() {
            Payment payment = newPayment();

            payment.cancel();

            assertThat(payment.status())
                    .isEqualTo(PaymentStatus.CANCELLED);
        }

        @Test
        void shouldCancelPendingPayment() {
            Payment payment = pendingPayment();

            payment.cancel();

            assertThat(payment.status())
                    .isEqualTo(PaymentStatus.CANCELLED);
        }

        @Test
        void shouldCancelAuthorizedPayment() {
            Payment payment = authorizedPayment();

            payment.cancel();

            assertThat(payment.status())
                    .isEqualTo(PaymentStatus.CANCELLED);
        }

        @Test
        void shouldCancelFailedPaymentAccordingToCurrentDomainContract() {
            Payment payment = pendingPayment();
            payment.markFailed("declined");

            payment.cancel();

            assertThat(payment.status())
                    .isEqualTo(PaymentStatus.CANCELLED);
        }

        @Test
        void cancellationShouldBeIdempotent() {
            Payment payment = newPayment();

            payment.cancel();
            Instant cancelledAt = payment.updatedAt();

            payment.cancel();

            assertThat(payment.status())
                    .isEqualTo(PaymentStatus.CANCELLED);

            assertThat(payment.updatedAt())
                    .isEqualTo(cancelledAt);
        }

        @Test
        void shouldRejectCancellationOfCapturedPayment() {
            Payment payment = capturedPayment();

            assertThatThrownBy(payment::cancel)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("captured payment cannot be cancelled");

            assertThat(payment.status())
                    .isEqualTo(PaymentStatus.CAPTURED);
        }
    }

    private Payment newPayment() {
        return Payment.create("order-42", DEFAULT_MONEY);
    }

    private Payment pendingPayment() {
        Payment payment = newPayment();
        payment.requestAuthorization();
        return payment;
    }

    private Payment authorizedPayment() {
        Payment payment = pendingPayment();
        payment.markAuthorized("provider-ref-1");
        return payment;
    }

    private Payment capturedPayment() {
        Payment payment = authorizedPayment();
        payment.capture();
        return payment;
    }
}