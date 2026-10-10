# Distributed tracing — SentinelPay

## Scope

All three Java services use Spring Boot 4 native `spring-boot-starter-opentelemetry`, Micrometer Observation and OTLP/HTTP trace export to Grafana Tempo. HTTP server spans, observed client requests and Kafka producer/consumer spans can be inspected under **Grafana → Explore → Tempo**. Prometheus remains the metrics backend.

The payment MCP `RestClient.Builder` now comes from Spring Boot auto-configuration, so client calls can participate in trace propagation. Kafka template and listener observations are enabled in `payment-platform`. W3C `traceparent` headers propagate over instrumented HTTP clients and Kafka record headers.

**Known boundary:** Payment creation writes a transactional outbox row. The scheduled publisher runs later, in a separate execution context. The Kafka publish and consume can share a trace, but the original payment HTTP span is **not** a parent of that later trace. To establish end-to-end causal links across the durable outbox, add persisted trace context (with a Flyway migration and an explicit propagation policy) in a separate change. Do not assume the entire payment lifecycle is one trace.

## Start

```bash
docker compose up -d --build
```

Open Grafana at http://localhost:3001 (existing local demo credentials). Choose Explore → Tempo and search `service.name` for:

- `sentinelpay-payment-platform`
- `sentinelpay-payment-mcp`
- `sentinelpay-ai-ops-agent`

Generate payment and agent requests following the existing README. A sampled HTTP request should produce a trace. A request passing from MCP through the payment API should have spans with a shared trace ID. Kafka messages should show producer/consumer spans. The sampling default is 100% for local verification, not a production recommendation.

## Configuration / operational behavior

- `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` defaults to `http://localhost:4318/v1/traces` outside Docker and `http://tempo:4318/v1/traces` inside Compose.
- `TRACING_SAMPLING_PROBABILITY` defaults to `1.0` for local development. Use e.g. `0.1` after traffic, privacy and performance testing in production.
- Tempo data is stored in the `tempo-data` Docker volume with 48-hour retention. Do not expose Tempo OTLP ingestion publicly.
- Do not put customer details, payment payloads, tokens, API keys or raw AI prompts in span attributes. Keep application log access controlled.
- Temporary collector/export outage must not prevent payment business logic; check exporter warning logs and Tempo ingestion after recovery.
- To roll back tracing, remove the OpenTelemetry starter dependencies and OTLP configuration, disable Kafka observation properties and stop the `tempo` service. No database migration or external API contract change is required for this patch.

## Verification checklist

1. `mvn -B -ntp verify` succeeds on Java 21.
2. `docker compose config` succeeds and all containers become healthy.
3. A single agent → MCP → payment API read produces one distributed HTTP trace with a shared trace ID.
4. A payment authorization event produces Kafka send/receive spans after the outbox poll.
5. No auth header, payment body or customer PII appears in trace attributes.
6. With Tempo stopped, payment requests still complete and exporter failures are only diagnostic.
