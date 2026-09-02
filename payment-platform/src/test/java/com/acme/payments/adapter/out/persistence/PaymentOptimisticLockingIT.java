package com.acme.payments.adapter.out.persistence;

import com.acme.payments.domain.Money;
import com.acme.payments.domain.Payment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "payments.outbox.publisher.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
@Testcontainers
class PaymentOptimisticLockingIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("payments")
                    .withUsername("payments")
                    .withPassword("payments");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private SpringDataPaymentRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void concurrentUpdateShouldRejectStaleEntity() {
        Payment payment = Payment.create(
                "optimistic-lock-001",
                Money.of("100.00", "EUR")
        );

        PaymentEntity stored = new PaymentEntity();
        stored.id = payment.id();
        stored.merchantReference = payment.merchantReference();
        stored.amount = payment.money().amount();
        stored.currency = payment.money().currency().getCurrencyCode();
        stored.status = payment.status();
        stored.createdAt = payment.createdAt();
        stored.updatedAt = payment.updatedAt();

        repository.saveAndFlush(stored);

        PaymentEntity first =
                transactionTemplate.execute(status ->
                        repository.findById(payment.id()).orElseThrow()
                );

        PaymentEntity second =
                transactionTemplate.execute(status ->
                        repository.findById(payment.id()).orElseThrow()
                );

        transactionTemplate.executeWithoutResult(status -> {
            first.failureReason = "first-update";
            repository.saveAndFlush(first);
        });

        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    second.failureReason = "stale-update";
                    repository.saveAndFlush(second);
                })
        ).isInstanceOfAny(
                ObjectOptimisticLockingFailureException.class,
                OptimisticLockException.class
        );
    }
}