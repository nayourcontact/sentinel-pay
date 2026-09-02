package com.acme.payments.ledger;

import com.acme.payments.application.PaymentRepository;
import com.acme.payments.domain.Money;
import com.acme.payments.domain.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerProjection")
class LedgerProjectionTest {

    @Mock
    private LedgerRepository ledger;

    @Mock
    private PaymentRepository payments;

    private LedgerProjection projection;

    @BeforeEach
    void setUp() {
        projection = new LedgerProjection(
                ledger,
                payments,
                new ObjectMapper()
        );
    }

    @Test
    void shouldCreateAuthorizationLedgerEntry() throws Exception {
        Payment payment = authorizedPayment();
        UUID paymentId = payment.id();

        when(
                ledger.existsByPaymentIdAndEntryType(
                        paymentId,
                        "AUTHORIZATION"
                )
        ).thenReturn(false);

        when(payments.findById(paymentId))
                .thenReturn(Optional.of(payment));

        projection.onAuthorized(
                "{\"paymentId\":\"" + paymentId + "\"}"
        );

        ArgumentCaptor<LedgerEntryEntity> captor =
                ArgumentCaptor.forClass(LedgerEntryEntity.class);

        verify(ledger).save(captor.capture());

        LedgerEntryEntity entry = captor.getValue();

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getPaymentId()).isEqualTo(paymentId);
        assertThat(entry.getEntryType()).isEqualTo("AUTHORIZATION");

        assertThat(entry.getAmount())
                .isEqualByComparingTo(new BigDecimal("149.90"));

        assertThat(entry.getCurrency()).isEqualTo("EUR");
        assertThat(entry.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldIgnoreDuplicateAuthorizationEvent() throws Exception {
        UUID paymentId = UUID.randomUUID();

        when(
                ledger.existsByPaymentIdAndEntryType(
                        paymentId,
                        "AUTHORIZATION"
                )
        ).thenReturn(true);

        projection.onAuthorized(
                "{\"paymentId\":\"" + paymentId + "\"}"
        );

        verify(
                ledger
        ).existsByPaymentIdAndEntryType(
                paymentId,
                "AUTHORIZATION"
        );

        verifyNoInteractions(payments);
        verify(ledger, never()).save(any());
    }

    @Test
    void shouldFailWhenPaymentDoesNotExist() {
        UUID paymentId = UUID.randomUUID();

        when(
                ledger.existsByPaymentIdAndEntryType(
                        paymentId,
                        "AUTHORIZATION"
                )
        ).thenReturn(false);

        when(payments.findById(paymentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> projection.onAuthorized(
                        "{\"paymentId\":\"" + paymentId + "\"}"
                )
        ).isInstanceOf(NoSuchElementException.class);

        verify(ledger, never()).save(any());
    }

    @Test
    void shouldRejectMalformedPayload() {
        assertThatThrownBy(
                () -> projection.onAuthorized("{invalid")
        ).isInstanceOf(Exception.class);

        verifyNoInteractions(ledger);
        verifyNoInteractions(payments);
    }

    private Payment authorizedPayment() {
        Payment payment = Payment.create(
                "ledger-test",
                Money.of("149.90", "EUR")
        );

        payment.requestAuthorization();
        payment.markAuthorized("provider-1");

        return payment;
    }
}