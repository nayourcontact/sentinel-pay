package com.acme.payments.adapter.out.persistence;

import com.acme.payments.domain.Money;
import com.acme.payments.domain.Payment;
import com.acme.payments.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.task.scheduling.enabled=false"
})
@Testcontainers
@DisplayName("Payment repository PostgreSQL integration")
class PaymentRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("payments")
                    .withUsername("payments")
                    .withPassword("payments");

    @DynamicPropertySource
    static void postgresProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );

        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
    }

    @Autowired
    private SpringDataPaymentRepository springRepository;

    @Autowired
    private JpaPaymentRepository repository;

    @BeforeEach
    void cleanDatabase() {
        springRepository.deleteAll();
    }

    @Test
    void shouldPersistAndReloadPayment() {
        Payment payment = Payment.create(
                "repository-order-001",
                Money.of("149.90", "EUR")
        );

        payment.requestAuthorization();

        Payment saved = repository.save(payment);

        Optional<Payment> loaded =
                repository.findById(saved.id());

        assertThat(loaded).isPresent();

        Payment result = loaded.orElseThrow();

        assertThat(result.id())
                .isEqualTo(payment.id());

        assertThat(result.merchantReference())
                .isEqualTo("repository-order-001");

        assertThat(result.money().amount())
                .isEqualByComparingTo("149.90");

        assertThat(
                result.money()
                        .currency()
                        .getCurrencyCode()
        ).isEqualTo("EUR");

        assertThat(result.status())
                .isEqualTo(
                        PaymentStatus.AUTHORIZATION_PENDING
                );
    }

    @Test
    void shouldFindPaymentByMerchantReference() {
        Payment payment = Payment.create(
                "repository-order-002",
                Money.of("20.00", "EUR")
        );

        repository.save(payment);

        Optional<Payment> result =
                repository.findByMerchantReference(
                        "repository-order-002"
                );

        assertThat(result).isPresent();

        assertThat(
                result.orElseThrow()
                        .merchantReference()
        ).isEqualTo("repository-order-002");
    }

    @Test
    void shouldReturnEmptyForUnknownMerchantReference() {
        assertThat(
                repository.findByMerchantReference(
                        "does-not-exist"
                )
        ).isEmpty();
    }

    @Test
    void databaseShouldRejectDuplicateMerchantReference() {
        Payment first = Payment.create(
                "duplicate-reference",
                Money.of("10.00", "EUR")
        );

        Payment second = Payment.create(
                "duplicate-reference",
                Money.of("20.00", "EUR")
        );

        repository.save(first);

        assertThatThrownBy(
                () -> {
                    repository.save(second);
                    springRepository.flush();
                }
        ).isInstanceOf(
                DataIntegrityViolationException.class
        );
    }
}