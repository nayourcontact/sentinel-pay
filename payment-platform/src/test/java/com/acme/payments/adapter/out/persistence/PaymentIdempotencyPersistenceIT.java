package com.acme.payments.adapter.out.persistence;

import com.acme.payments.application.PaymentApplicationService;
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
@DisplayName("Payment persistence idempotency")
class PaymentIdempotencyPersistenceIT {

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
    private SpringDataPaymentRepository payments;

    @Autowired
    private OutboxJpaRepository outbox;

    @BeforeEach
    void clean() {
        outbox.deleteAll();
        payments.deleteAll();
    }

    @Test
    void duplicateRequestMustCreateOnlyOnePaymentAndOneAuthorizationEvent() {
        var command =
                new PaymentApplicationService.CreatePayment(
                        "idempotency-db-001",
                        "149.90",
                        "EUR"
                );

        var first = service.create(command);
        var second = service.create(command);

        assertThat(first.id())
                .isEqualTo(second.id());

        assertThat(payments.count())
                .isEqualTo(1);

        assertThat(outbox.count())
                .isEqualTo(1);

        var events = outbox.findAll();

        assertThat(events)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.eventType)
                            .isEqualTo(
                                    "payment.authorization.requested"
                            );

                    assertThat(event.aggregateId)
                            .isEqualTo(first.id());
                });
    }
}