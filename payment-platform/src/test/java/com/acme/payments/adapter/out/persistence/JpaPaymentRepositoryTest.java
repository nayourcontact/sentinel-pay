package com.acme.payments.adapter.out.persistence;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JpaPaymentRepository")
class JpaPaymentRepositoryTest {

    @Mock
    private SpringDataPaymentRepository delegate;

    private JpaPaymentRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaPaymentRepository(delegate);
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        void shouldPersistNewPayment() {
            Payment payment = Payment.create(
                    "order-100",
                    Money.of("149.90", "EUR")
            );

            payment.requestAuthorization();
            payment.markAuthorized("provider-123");

            when(delegate.findById(payment.id()))
                    .thenReturn(Optional.empty());

            when(delegate.save(any(PaymentEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Payment result = repository.save(payment);

            ArgumentCaptor<PaymentEntity> captor =
                    ArgumentCaptor.forClass(PaymentEntity.class);

            verify(delegate).save(captor.capture());

            PaymentEntity entity = captor.getValue();

            assertThat(entity.id).isEqualTo(payment.id());
            assertThat(entity.merchantReference)
                    .isEqualTo("order-100");

            assertThat(entity.amount)
                    .isEqualByComparingTo(new BigDecimal("149.90"));

            assertThat(entity.currency).isEqualTo("EUR");

            assertThat(entity.status)
                    .isEqualTo(PaymentStatus.AUTHORIZED);

            assertThat(entity.providerReference)
                    .isEqualTo("provider-123");

            assertThat(entity.failureReason).isNull();
            assertThat(entity.createdAt)
                    .isEqualTo(payment.createdAt());
            assertThat(entity.updatedAt)
                    .isEqualTo(payment.updatedAt());

            assertThat(result.id())
                    .isEqualTo(payment.id());

            assertThat(result.status())
                    .isEqualTo(PaymentStatus.AUTHORIZED);

            assertThat(result.providerReference())
                    .isEqualTo("provider-123");
        }

        @Test
        void shouldReuseExistingEntityWhenUpdatingPayment() {
            Payment payment = Payment.create(
                    "order-update",
                    Money.of("25.00", "USD")
            );

            payment.requestAuthorization();

            PaymentEntity existing = new PaymentEntity();
            existing.id = payment.id();
            existing.merchantReference = "old-reference";
            existing.amount = BigDecimal.ONE;
            existing.currency = "EUR";
            existing.status = PaymentStatus.CREATED;
            existing.createdAt = Instant.now();
            existing.updatedAt = Instant.now();

            when(delegate.findById(payment.id()))
                    .thenReturn(Optional.of(existing));

            when(delegate.save(existing))
                    .thenReturn(existing);

            Payment result = repository.save(payment);

            verify(delegate).save(existing);

            assertThat(existing.merchantReference)
                    .isEqualTo("order-update");

            assertThat(existing.amount)
                    .isEqualByComparingTo("25.00");

            assertThat(existing.currency)
                    .isEqualTo("USD");

            assertThat(existing.status)
                    .isEqualTo(PaymentStatus.AUTHORIZATION_PENDING);

            assertThat(result.status())
                    .isEqualTo(PaymentStatus.AUTHORIZATION_PENDING);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        void shouldMapEntityToDomainPayment() {
            UUID id = UUID.randomUUID();

            PaymentEntity entity = entity(
                    id,
                    "order-find",
                    "99.50",
                    "EUR",
                    PaymentStatus.AUTHORIZED
            );

            entity.providerReference = "provider-x";
            entity.version = 5;

            when(delegate.findById(id))
                    .thenReturn(Optional.of(entity));

            Optional<Payment> result =
                    repository.findById(id);

            assertThat(result).isPresent();

            Payment payment = result.orElseThrow();

            assertThat(payment.id()).isEqualTo(id);
            assertThat(payment.merchantReference())
                    .isEqualTo("order-find");

            assertThat(payment.money().amount())
                    .isEqualByComparingTo("99.50");

            assertThat(payment.money().currency()
                    .getCurrencyCode()).isEqualTo("EUR");

            assertThat(payment.status())
                    .isEqualTo(PaymentStatus.AUTHORIZED);

            assertThat(payment.providerReference())
                    .isEqualTo("provider-x");

            assertThat(payment.version()).isEqualTo(5);
        }

        @Test
        void shouldReturnEmptyWhenPaymentDoesNotExist() {
            UUID id = UUID.randomUUID();

            when(delegate.findById(id))
                    .thenReturn(Optional.empty());

            assertThat(repository.findById(id))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("findByMerchantReference")
    class FindByMerchantReference {

        @Test
        void shouldFindAndMapPayment() {
            PaymentEntity entity = entity(
                    UUID.randomUUID(),
                    "merchant-42",
                    "10.00",
                    "EUR",
                    PaymentStatus.FAILED
            );

            entity.failureReason = "declined";

            when(delegate.findByMerchantReference("merchant-42"))
                    .thenReturn(Optional.of(entity));

            Optional<Payment> result =
                    repository.findByMerchantReference("merchant-42");

            assertThat(result).isPresent();

            Payment payment = result.orElseThrow();

            assertThat(payment.merchantReference())
                    .isEqualTo("merchant-42");

            assertThat(payment.status())
                    .isEqualTo(PaymentStatus.FAILED);

            assertThat(payment.failureReason())
                    .isEqualTo("declined");
        }

        @Test
        void shouldReturnEmptyWhenMerchantReferenceDoesNotExist() {
            when(delegate.findByMerchantReference("missing"))
                    .thenReturn(Optional.empty());

            assertThat(
                    repository.findByMerchantReference("missing")
            ).isEmpty();
        }
    }

    private PaymentEntity entity(
            UUID id,
            String merchantReference,
            String amount,
            String currency,
            PaymentStatus status
    ) {
        PaymentEntity entity = new PaymentEntity();

        entity.id = id;
        entity.merchantReference = merchantReference;
        entity.amount = new BigDecimal(amount);
        entity.currency = currency;
        entity.status = status;

        entity.createdAt =
                Instant.parse("2026-01-01T10:00:00Z");

        entity.updatedAt =
                Instant.parse("2026-01-01T10:01:00Z");

        return entity;
    }
}