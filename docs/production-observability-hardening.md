# SentinelPay — OpenTelemetry production hardening

## Scope

This change does **not** change the database schema, Flyway migrations, business APIs, MCP session handling, Kafka topics, or outbox trace-link semantics.

## Sampling

All three Spring Boot applications support `TRACING_SAMPLING_PROBABILITY`, wired to `management.tracing.sampling.probability`.

- Local integration validation: `TRACING_SAMPLING_PROBABILITY=1.0`.
- Production starting point: `TRACING_SAMPLING_PROBABILITY=0.1` (tune using actual throughput, error budget and collector capacity).
- A sampler probability below 1.0 means individual traces may not be collected: do not treat a missing trace as a failed request.
- When root sampling drops an originating request, the outbox link may point to an unexported trace. This is expected under independent asynchronous sampling; include business identifiers in a separate authorized lookup workflow when necessary.

## Privacy

The agent installs a Micrometer `ObservationFilter` removing high-cardinality fields that can carry user prompts, retrieved RAG documents, tool inputs and tool outputs. Optional Spring AI prompt/completion/tool/document capture is explicitly disabled as well.

Do not put payment card data, secrets, PII or raw request bodies into custom span attributes, URLs, log messages or exception strings. This filter protects listed Micrometer observation attributes, **not** every custom OpenTelemetry span written via the low-level API, exporter-generated data or application logs. Audit those separately before deployment.

Old traces in Tempo are **not scrubbed** by this change; apply retention/erasure policy to historic traces separately.

## Validation

1. Build and test: `docker compose build ai-ops-agent` (the Dockerfile skips tests). For tests, execute `mvn -pl ai-ops-agent -am test` in an environment with Maven.
2. Recreate the agent only: `docker compose up -d --no-deps ai-ops-agent`.
3. Call `/api/v1/chat` with a unique test phrase and a valid test payment UUID. Confirm tool calls continue to work.
4. Export the new trace from Tempo and ensure its `pg_vector query` span **does not** contain `db.vector.query.content` or `db.vector.query.response.documents`; `db.collection.name` and `db.vector.query.top_k` should remain.
5. Confirm agent->MCP->payment trace parentage; leave the existing V1/V2/V3 Flyway migration history unchanged.
6. In a staging environment set `TRACING_SAMPLING_PROBABILITY=0.1`, recreate the services and inspect representative sampling across many requests. Restore 1.0 for deterministic trace debugging.

## Deployment cautions

The existing Docker Compose stack includes development credentials and local observability endpoints. They must be replaced with managed secrets, TLS, restricted access, appropriate retention settings, and authenticated collectors before production deployment. Do not expose Grafana, Tempo, Prometheus or Actuator metrics publicly by default.

### Offline Tempo export checker

Run `python scripts/verify-observability-export.py <tempo-export.json>` to flag configured sensitive attributes and show trace links. A passing result is a targeted check, not a complete privacy certification.
