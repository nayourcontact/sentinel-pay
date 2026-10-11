package com.acme.payments.adapter.out.persistence;

import com.acme.payments.application.PaymentApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.task.scheduling.enabled=false"
})
@Testcontainers
class PaymentConcurrentIdempotencyIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("payments").withUsername("payments").withPassword("payments");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired PaymentApplicationService service;
    @Autowired SpringDataPaymentRepository payments;
    @Autowired OutboxJpaRepository outbox;

    @Test
    void fiveConcurrentCreatesReturnSamePaymentAndSingleOutboxEvent() throws Exception {
        String reference = "concurrency-" + UUID.randomUUID();
        var command = new PaymentApplicationService.CreatePayment(reference, "10.00", "HUF");
        var ready = new CountDownLatch(5);
        var start = new CountDownLatch(1);
        List<Callable<UUID>> workers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            workers.add(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return service.create(command).id();
            });
        }
        try (var pool = Executors.newFixedThreadPool(5)) {
            var results = workers.stream().map(pool::submit).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            UUID first = results.get(0).get(30, TimeUnit.SECONDS);
            for (var result : results) assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(first);
            assertThat(payments.findByMerchantReference(reference)).isPresent();
            assertThat(outbox.findAll().stream().filter(e -> first.equals(e.aggregateId)).count()).isEqualTo(1);
        } finally {
            start.countDown();
        }
    }
}
