package com.acme.payments.adapter.out.persistence;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JpaOutbox")
class JpaOutboxTest {

    @Mock
    private OutboxJpaRepository repository;

    private JpaOutbox outbox;

    @BeforeEach
    void setUp() {
        outbox = new JpaOutbox(repository);
    }

    @Test
    void shouldCreateAndPersistOutboxEvent() {
        UUID aggregateId = UUID.randomUUID();

        Instant before = Instant.now();

        outbox.append(
                "Payment",
                aggregateId,
                "payment.authorized",
                "{\"paymentId\":\"" + aggregateId + "\"}"
        );

        Instant after = Instant.now();

        ArgumentCaptor<OutboxEventEntity> captor =
                ArgumentCaptor.forClass(OutboxEventEntity.class);

        verify(repository).save(captor.capture());
        verifyNoMoreInteractions(repository);

        OutboxEventEntity event = captor.getValue();

        assertThat(event.id).isNotNull();
        assertThat(event.aggregateType).isEqualTo("Payment");
        assertThat(event.aggregateId).isEqualTo(aggregateId);
        assertThat(event.eventType).isEqualTo("payment.authorized");
        assertThat(event.payload)
                .isEqualTo("{\"paymentId\":\"" + aggregateId + "\"}");

        assertThat(event.createdAt)
                .isBetween(before, after);

        assertThat(event.attempts).isZero();
        assertThat(event.publishedAt).isNull();
        assertThat(event.failedAt).isNull();
        assertThat(event.lastError).isNull();
    }

    @Test
    void shouldGenerateDifferentIdsForDifferentEvents() {
        UUID aggregateId = UUID.randomUUID();

        outbox.append(
                "Payment",
                aggregateId,
                "event.one",
                "{}"
        );

        outbox.append(
                "Payment",
                aggregateId,
                "event.two",
                "{}"
        );

        ArgumentCaptor<OutboxEventEntity> captor =
                ArgumentCaptor.forClass(OutboxEventEntity.class);

        verify(repository, times(2))
                .save(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(event -> event.id)
                .doesNotHaveDuplicates();
    }
    @Test
    void shouldStoreOriginIdentifiersWhenSpanIsActive() {
        var context = SpanContext.create("0123456789abcdef0123456789abcdef", "0123456789abcdef",
                TraceFlags.getSampled(), TraceState.getDefault());
        var span = Span.wrap(context);
        try (Scope ignored = span.makeCurrent()) {
            outbox.append("Payment", UUID.randomUUID(), "payment.authorized", "{}");
        }
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().originTraceId).isEqualTo(context.getTraceId());
        assertThat(captor.getValue().originSpanId).isEqualTo(context.getSpanId());
        assertThat(captor.getValue().originTraceFlags).isEqualTo("01");
    }

}