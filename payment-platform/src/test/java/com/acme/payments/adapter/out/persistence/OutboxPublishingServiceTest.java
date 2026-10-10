package com.acme.payments.adapter.out.persistence;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPublishingService")
class OutboxPublishingServiceTest {

    @Mock
    private OutboxJpaRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafka;

    private OutboxPublishingService service;

    @BeforeEach
    void setUp() {
        service = new OutboxPublishingService(
                repository,
                kafka,
                OpenTelemetry.noop()
        );
    }

    @Nested
    @DisplayName("successful publishing")
    class SuccessfulPublishing {

        @Test
        void shouldPublishPendingEventAndMarkItPublished() {
            OutboxEventEntity event = event();

            when(repository.lockNextBatch(100))
                    .thenReturn(List.of(event));

            when(kafka.send(
                    event.eventType,
                    event.aggregateId.toString(),
                    event.payload
            )).thenReturn(
                    CompletableFuture.completedFuture(null)
            );

            Instant before = Instant.now();

            service.publishBatch();

            Instant after = Instant.now();

            verify(kafka).send(
                    event.eventType,
                    event.aggregateId.toString(),
                    event.payload
            );

            assertThat(event.publishedAt)
                    .isBetween(before, after);

            assertThat(event.failedAt).isNull();
            assertThat(event.attempts).isZero();
            assertThat(event.lastError).isNull();
        }

        @Test
        void shouldProcessAllLockedEvents() {
            OutboxEventEntity first = event();
            OutboxEventEntity second = event();

            when(repository.lockNextBatch(100))
                    .thenReturn(List.of(first, second));

            when(kafka.send(
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(
                    CompletableFuture.completedFuture(null)
            );

            service.publishBatch();

            verify(kafka, times(2))
                    .send(
                            anyString(),
                            anyString(),
                            anyString()
                    );

            assertThat(first.publishedAt).isNotNull();
            assertThat(second.publishedAt).isNotNull();
        }

        @Test
        void shouldDoNothingWhenNoEventsAreAvailable() {
            when(repository.lockNextBatch(100))
                    .thenReturn(List.of());

            service.publishBatch();

            verify(repository).lockNextBatch(100);
            verifyNoInteractions(kafka);
        }
    }

    @Nested
    @DisplayName("failed publishing")
    class FailedPublishing {

        @Test
        void shouldIncrementAttemptsAndStoreErrorWhenKafkaFails() {
            OutboxEventEntity event = event();

            when(repository.lockNextBatch(100))
                    .thenReturn(List.of(event));

            when(kafka.send(
                    event.eventType,
                    event.aggregateId.toString(),
                    event.payload
            )).thenReturn(
                    CompletableFuture.failedFuture(
                            new IllegalStateException(
                                    "broker unavailable"
                            )
                    )
            );

            service.publishBatch();

            assertThat(event.publishedAt).isNull();
            assertThat(event.failedAt).isNull();

            assertThat(event.attempts).isEqualTo(1);

            /*
             * Future#get wraps the actual failure,
             * therefore the caught exception is typically
             * ExecutionException.
             */
            assertThat(event.lastError)
                    .contains("ExecutionException")
                    .contains("broker unavailable");
        }

        @Test
        void shouldNotQuarantineEventBeforeTenthFailure() {
            OutboxEventEntity event = event();
            event.attempts = 8;

            when(repository.lockNextBatch(100))
                    .thenReturn(List.of(event));

            when(kafka.send(
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(
                    CompletableFuture.failedFuture(
                            new RuntimeException("failure")
                    )
            );

            service.publishBatch();

            assertThat(event.attempts).isEqualTo(9);
            assertThat(event.failedAt).isNull();
            assertThat(event.publishedAt).isNull();
        }

        @Test
        void shouldQuarantineEventOnTenthFailure() {
            OutboxEventEntity event = event();
            event.attempts = 9;

            when(repository.lockNextBatch(100))
                    .thenReturn(List.of(event));

            when(kafka.send(
                    anyString(),
                    anyString(),
                    anyString()
            )).thenReturn(
                    CompletableFuture.failedFuture(
                            new RuntimeException(
                                    "permanent failure"
                            )
                    )
            );

            Instant before = Instant.now();

            service.publishBatch();

            Instant after = Instant.now();

            assertThat(event.attempts)
                    .isEqualTo(10);

            assertThat(event.failedAt)
                    .isBetween(before, after);

            assertThat(event.publishedAt)
                    .isNull();

            assertThat(event.lastError)
                    .contains("ExecutionException")
                    .contains("permanent failure");
        }

        @Test
        void shouldContinueProcessingAfterOneEventFails() {
            OutboxEventEntity failing = event();
            OutboxEventEntity succeeding = event();

            when(repository.lockNextBatch(100))
                    .thenReturn(
                            List.of(failing, succeeding)
                    );

            when(kafka.send(
                    failing.eventType,
                    failing.aggregateId.toString(),
                    failing.payload
            )).thenReturn(
                    CompletableFuture.failedFuture(
                            new RuntimeException("failure")
                    )
            );

            when(kafka.send(
                    succeeding.eventType,
                    succeeding.aggregateId.toString(),
                    succeeding.payload
            )).thenReturn(
                    CompletableFuture.completedFuture(null)
            );

            service.publishBatch();

            assertThat(failing.attempts)
                    .isEqualTo(1);

            assertThat(failing.publishedAt)
                    .isNull();

            assertThat(succeeding.publishedAt)
                    .isNotNull();

            verify(kafka, times(2))
                    .send(
                            anyString(),
                            anyString(),
                            anyString()
                    );
        }
    }

    @Test
    void shouldLinkSavedOriginToNewOutboxPublishSpan() throws Exception {
        var telemetry = mock(OpenTelemetry.class);
        var tracer = mock(io.opentelemetry.api.trace.Tracer.class);
        var builder = mock(io.opentelemetry.api.trace.SpanBuilder.class);
        var span = mock(io.opentelemetry.api.trace.Span.class);
        when(telemetry.getTracer("sentinelpay.outbox")).thenReturn(tracer);
        when(tracer.spanBuilder("outbox.publish.event")).thenReturn(builder);
        when(builder.setNoParent()).thenReturn(builder);
        when(builder.setSpanKind(any())).thenReturn(builder);
        when(builder.setAttribute(anyString(), anyString())).thenReturn(builder);
        when(builder.addLink(any(io.opentelemetry.api.trace.SpanContext.class))).thenReturn(builder);
        when(builder.startSpan()).thenReturn(span);
        when(span.makeCurrent()).thenReturn(() -> {});
        var linkedService = new OutboxPublishingService(repository, kafka, telemetry);
        OutboxEventEntity event = event();
        event.originTraceId = "0123456789abcdef0123456789abcdef";
        event.originSpanId = "0123456789abcdef";
        event.originTraceFlags = "01";
        when(repository.lockNextBatch(100)).thenReturn(List.of(event));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        linkedService.publishBatch();
        var link = org.mockito.ArgumentCaptor.forClass(io.opentelemetry.api.trace.SpanContext.class);
        verify(builder).addLink(link.capture());
        assertThat(link.getValue().getTraceId()).isEqualTo(event.originTraceId);
        assertThat(link.getValue().getSpanId()).isEqualTo(event.originSpanId);
        verify(span).end();
        assertThat(event.publishedAt).isNotNull();
    }

    private OutboxEventEntity event() {
        OutboxEventEntity event =
                new OutboxEventEntity();

        event.id = UUID.randomUUID();
        event.aggregateType = "Payment";
        event.aggregateId = UUID.randomUUID();
        event.eventType =
                "payment.authorization.requested";

        event.payload =
                "{\"paymentId\":\""
                        + event.aggregateId
                        + "\"}";

        event.createdAt = Instant.now();
        event.attempts = 0;

        return event;
    }
}