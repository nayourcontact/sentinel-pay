package com.acme.payments.application;

import com.acme.payments.domain.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.task.scheduling.enabled=false"
})
@Testcontainers
@DisplayName("Payment idempotency integration")
class PaymentIdempotencyIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("payments")
                    .withUsername("payments")
                    .withPassword("payments");

    @DynamicPropertySource
    static void properties(
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
    private PaymentApplicationService service;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private Outbox outbox;

    @Test
    void sameIdempotencyKeyShouldReturnSamePayment() {
        var firstCommand =
                new PaymentApplicationService.CreatePayment(
                        "idempotency-order-001",
                        "149.90",
                        "EUR"
                );

        var secondCommand =
                new PaymentApplicationService.CreatePayment(
                        "idempotency-order-001",
                        "999.00",
                        "USD"
                );

        Payment first =
                service.create(firstCommand);

        Payment second =
                service.create(secondCommand);

        assertThat(second.id())
                .isEqualTo(first.id());

        assertThat(
                second.merchantReference()
        ).isEqualTo(
                "idempotency-order-001"
        );

        /*
         * A második request body nem írhatja
         * felül az első request eredményét.
         */
        assertThat(second.money().amount())
                .isEqualByComparingTo("149.90");

        assertThat(
                second.money()
                        .currency()
                        .getCurrencyCode()
        ).isEqualTo("EUR");
    }
}