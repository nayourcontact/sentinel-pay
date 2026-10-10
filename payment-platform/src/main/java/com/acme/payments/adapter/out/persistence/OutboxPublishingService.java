package com.acme.payments.adapter.out.persistence;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OutboxPublishingService {
    private final OutboxJpaRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final io.opentelemetry.api.trace.Tracer tracer;

    public OutboxPublishingService(OutboxJpaRepository repository, KafkaTemplate<String, String> kafka, OpenTelemetry telemetry) {
        this.repository = repository;
        this.kafka = kafka;
        this.tracer = telemetry.getTracer("sentinelpay.outbox");
    }

    @Transactional
    public void publishBatch() {
        for (var event : repository.lockNextBatch(100)) {
            try {
                publishWithTraceLink(event);
                event.publishedAt = Instant.now();
            } catch (Exception ex) {
                event.attempts++;
                event.lastError = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
                if (event.attempts >= 10) {
                    event.failedAt = Instant.now(); // durable quarantine: never masquerade a failed event as published
                }
            }
        }
    }

    private void publishWithTraceLink(OutboxEventEntity event) throws Exception {
        // A durable outbox is an asynchronous boundary: use a new root and link
        // to the originating request instead of pretending it is still active.
        var builder = tracer.spanBuilder("outbox.publish.event")
                .setNoParent()
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("outbox.event.id", event.id.toString())
                .setAttribute("outbox.event.type", event.eventType);
        if (event.originTraceId != null && event.originSpanId != null && event.originTraceFlags != null
                && event.originTraceId.matches("[0-9a-f]{32}")
                && event.originSpanId.matches("[0-9a-f]{16}")
                && event.originTraceFlags.matches("[0-9a-f]{2}")) {
            var origin = SpanContext.createFromRemoteParent(
                    event.originTraceId, event.originSpanId,
                    TraceFlags.fromHex(event.originTraceFlags, 0), TraceState.getDefault());
            if (origin.isValid()) builder.addLink(origin);
        }
        var span = builder.startSpan();
        try (Scope ignored = span.makeCurrent()) {
            // KafkaTemplate observation creates the producer span beneath this new root.
            kafka.send(event.eventType, event.aggregateId.toString(), event.payload).get();
        } catch (Exception ex) {
            span.recordException(ex);
            throw ex;
        } finally {
            span.end();
        }
    }
}
