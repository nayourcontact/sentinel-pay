package com.acme.payments.adapter.out.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.task.scheduling.enabled=false"
})
@Testcontainers
@DisplayName("Outbox PostgreSQL integration")
class OutboxRepositoryIT {

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
    private OutboxJpaRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    @Transactional
    void shouldLockPendingEvents() {
        OutboxEventEntity event =
                event("payment.test");

        repository.saveAndFlush(event);

        List<OutboxEventEntity> result =
                repository.lockNextBatch(100);

        assertThat(result)
                .extracting(e -> e.id)
                .containsExactly(event.id);
    }

    @Test
    @Transactional
    void shouldNotReturnPublishedEvents() {
        OutboxEventEntity event =
                event("payment.test");

        event.publishedAt = Instant.now();

        repository.saveAndFlush(event);

        List<OutboxEventEntity> result =
                repository.lockNextBatch(100);

        assertThat(result).isEmpty();
    }

    @Test
    @Transactional
    void shouldNotReturnPermanentlyFailedEvents() {
        OutboxEventEntity event =
                event("payment.test");

        event.failedAt = Instant.now();

        repository.saveAndFlush(event);

        List<OutboxEventEntity> result =
                repository.lockNextBatch(100);

        assertThat(result).isEmpty();
    }

    @Test
    @Transactional
    void shouldReturnPendingEventsInCreationOrder() {
        OutboxEventEntity first =
                event("event.first");

        first.createdAt =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        OutboxEventEntity second =
                event("event.second");

        second.createdAt =
                Instant.parse(
                        "2026-01-01T10:01:00Z"
                );

        repository.save(first);
        repository.save(second);
        repository.flush();

        List<OutboxEventEntity> result =
                repository.lockNextBatch(100);

        assertThat(result)
                .extracting(e -> e.id)
                .containsExactly(
                        first.id,
                        second.id
                );
    }

    private OutboxEventEntity event(
            String eventType
    ) {
        OutboxEventEntity event =
                new OutboxEventEntity();

        event.id = UUID.randomUUID();
        event.aggregateType = "Payment";
        event.aggregateId = UUID.randomUUID();
        event.eventType = eventType;
        event.payload = "{}";
        event.createdAt = Instant.now();
        event.attempts = 0;

        return event;
    }
}