package com.acme.payments.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Money")
class MoneyTest {

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        void shouldCreateMoneyWithCurrencySpecificScale() {
            Money money = Money.of("42", "EUR");

            assertThat(money.amount())
                    .isEqualByComparingTo(new BigDecimal("42.00"));

            assertThat(money.amount().scale()).isEqualTo(2);
            assertThat(money.currency()).isEqualTo(Currency.getInstance("EUR"));
        }

        @Test
        void shouldCreateMoneyWithAlreadyCorrectScale() {
            Money money = Money.of("149.90", "EUR");

            assertThat(money.amount())
                    .isEqualByComparingTo("149.90");

            assertThat(money.currency().getCurrencyCode())
                    .isEqualTo("EUR");
        }

        @Test
        void shouldRespectZeroFractionCurrency() {
            Money money = Money.of("100", "JPY");

            assertThat(money.amount())
                    .isEqualByComparingTo("100");

            assertThat(money.amount().scale()).isZero();
        }

        @Test
        void shouldNormalizeEquivalentAmounts() {
            Money fromInteger = Money.of("42", "EUR");
            Money fromDecimal = Money.of("42.00", "EUR");

            assertThat(fromInteger).isEqualTo(fromDecimal);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        void shouldRejectZeroAmount() {
            assertThatThrownBy(() -> Money.of("0.00", "EUR"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("amount must be positive");
        }

        @Test
        void shouldRejectNegativeAmount() {
            assertThatThrownBy(() -> Money.of("-0.01", "EUR"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("amount must be positive");
        }

        @Test
        void shouldRejectUnsupportedFractionalPrecision() {
            assertThatThrownBy(() -> Money.of("10.001", "EUR"))
                    .isInstanceOf(ArithmeticException.class);
        }

        @Test
        void shouldRejectFractionForZeroFractionCurrency() {
            assertThatThrownBy(() -> Money.of("100.50", "JPY"))
                    .isInstanceOf(ArithmeticException.class);
        }

        @Test
        void shouldRejectNullAmount() {
            assertThatThrownBy(
                    () -> new Money(null, Currency.getInstance("EUR"))
            )
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("amount");
        }

        @Test
        void shouldRejectNullCurrency() {
            assertThatThrownBy(
                    () -> new Money(BigDecimal.TEN, null)
            )
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("currency");
        }

        @Test
        void shouldRejectUnknownCurrency() {
            assertThatThrownBy(() -> Money.of("10.00", "NOT_A_CURRENCY"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}