package com.acme.payments.adapter.out.provider;

import com.acme.payments.application.PaymentProvider;
import com.acme.payments.domain.Money;
import com.acme.payments.domain.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DeterministicMockPaymentProvider")
class DeterministicMockPaymentProviderTest {

    private final DeterministicMockPaymentProvider provider =
            new DeterministicMockPaymentProvider();

    @Test
    void shouldDeclinePaymentWhenMerchantReferenceFallsIntoDeclineBucket() {
        Payment payment = Payment.create(
                findMerchantReferenceForBucket(0),
                Money.of("149.90", "EUR")
        );

        PaymentProvider.AuthorizationResult result =
                provider.authorize(payment);

        assertThat(result)
                .isInstanceOf(PaymentProvider.Declined.class);

        PaymentProvider.Declined declined =
                (PaymentProvider.Declined) result;

        assertThat(declined.reason())
                .isEqualTo("provider_declined");
    }

    @Test
    void shouldAuthorizePaymentWhenMerchantReferenceDoesNotFallIntoDeclineBucket() {
        Payment payment = Payment.create(
                findMerchantReferenceOutsideBucket(0),
                Money.of("149.90", "EUR")
        );

        PaymentProvider.AuthorizationResult result =
                provider.authorize(payment);

        assertThat(result)
                .isInstanceOf(PaymentProvider.Authorized.class);

        PaymentProvider.Authorized authorized =
                (PaymentProvider.Authorized) result;

        assertThat(authorized.providerReference())
                .isEqualTo(
                        "mock_" + payment.id()
                                .toString()
                                .replace("-", "")
                );
    }

    @Test
    void shouldProduceDeterministicDecisionForSameMerchantReference() {
        String merchantReference =
                findMerchantReferenceOutsideBucket(0);

        Payment first = Payment.create(
                merchantReference,
                Money.of("10.00", "EUR")
        );

        Payment second = Payment.create(
                merchantReference,
                Money.of("20.00", "EUR")
        );

        PaymentProvider.AuthorizationResult firstResult =
                provider.authorize(first);

        PaymentProvider.AuthorizationResult secondResult =
                provider.authorize(second);

        assertThat(firstResult.getClass())
                .isEqualTo(secondResult.getClass());
    }

    private String findMerchantReferenceForBucket(int expectedBucket) {
        for (int i = 0; i < 10_000; i++) {
            String candidate = "merchant-" + i;

            int bucket =
                    Math.floorMod(candidate.hashCode(), 10);

            if (bucket == expectedBucket) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "Could not find merchant reference for bucket "
                        + expectedBucket
        );
    }

    private String findMerchantReferenceOutsideBucket(int excludedBucket) {
        for (int i = 0; i < 10_000; i++) {
            String candidate = "merchant-" + i;

            int bucket =
                    Math.floorMod(candidate.hashCode(), 10);

            if (bucket != excludedBucket) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "Could not find merchant reference outside bucket "
                        + excludedBucket
        );
    }
}