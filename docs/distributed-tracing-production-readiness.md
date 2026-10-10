# SentinelPay — Distributed Tracing / OpenTelemetry release checklist

## Architecture

1. `POST /api/payments` creates the payment and transactional outbox entry.
2. The request's trace/span IDs are persisted with the outbox entry (Flyway V3, **do not edit existing V1–V3 migration scripts**).
3. A separately scheduled outbox publisher creates an `outbox.publish.event` span with an **OpenTelemetry Span Link** to the originating payment request (not a parent-child relationship).
4. Spring Kafka produces an observed message; message headers propagate trace context. `ledger-projection` and `webhook-projection` consumers produce spans parented by Kafka producer.
5. The AI Agent HTTP request propagates through MCP tool HTTP client/server to the payment platform; Ollama/RAG spans are recorded without request message content.
6. OTLP/HTTP exporter sends spans to Tempo; Grafana queries Tempo.

## Configuration

| Setting | Local default | Staging/production guidance |
| --- | --- | --- |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` | Begin testing with `0.1` and tune, applied to all three Java services |
| `TEMPO_BLOCK_RETENTION` | `48h` | `168h` is an example for staging only; assess volume, privacy and obligations |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://tempo:4318/v1/traces` | Enforce transport authentication/TLS via controlled collector |

**Important:** This Docker Compose configuration is a local/demo stack. The Tempo storage backend remains local volume storage; for highly available production use supported remote object storage. Local retention does not guarantee hard secure erasure. Do not use demo credentials or publicly expose management ports. Outbox links may point to unsampled/missing upstream traces; missing traces are not proof of a processing failure. Avoid assuming uniform sampling guarantees an entire asynchronous workflow will be exported.

## Deployment (preserve database)

```powershell
# 0. Back up your current working tree / commit first
# 1. Extract updated project files WITHOUT replacing your existing .env or .git
# 2. Validate the Compose config and see resolved environment values
docker compose config --quiet
# 3. Recreate Tempo to pick up env-expanded retention; keeps named volume
docker compose up -d --no-deps --force-recreate tempo
# 4. Recreate app services only if sampling settings changed
docker compose up -d payment-platform payment-mcp-server ai-ops-agent
# 5. Check health
docker compose ps payment-platform payment-mcp-server ai-ops-agent tempo
```

Do **not** run `docker compose down -v`, `flyway repair`, or edit any historical migration solely to deploy tracing configuration. Ensure new environment variables in `.env` have desired values before restart. Avoid `--no-deps` when dependencies are not running: AI Agent MCP client initialization requires a healthy MCP server.

## Test protocol

Use `TRACING_SAMPLING_PROBABILITY=1.0` while validating the expected presence of spans. In Grafana Tempo export these three *separate* trace types as JSON:

1. AI Agent call invoking `get_payment`: has Agent HTTP client -> MCP HTTP server, MCP HTTP client -> Platform HTTP server; `db.vector.query.content` **absent**.
2. Kafka payment event: producer and `ledger-projection` / `webhook-projection` consumers form valid parent-child relations.
3. Outbox: `outbox.publish.event` has a Span Link referencing the initial HTTP request context.

Run each with the dedicated requirement:

```powershell
python .\scripts\verify-observability-export.py '.\exports\agent.json' --require http
python .\scripts\verify-observability-export.py '.\exports\kafka.json' --require kafka
python .\scripts\verify-observability-export.py '.\exports\outbox.json' --require outbox
python -m unittest discover -s scripts/tests -v
```

The export verifier checks parent identifiers only within each provided export; some partial Tempo exports may warn about missing parents. The checker intentionally **warns** about raw payment IDs in HTTP client URLs rather than redacting them automatically, since the corresponding instrumentation change requires separate validation. Logs, exception messages, baggage, resource attributes and direct OTel API spans require independent reviews.

## Operational checks

- Verify sampling and retention on **fresh** traces; changing sampling is not retroactive, changing retention does not remove already-exported sensitive data immediately.
- Monitor OTLP exporter failures and trace ingestion, Tempo storage usage, Grafana query latency and service startup health.
- Sampling probability is a policy: do not expect every test call to appear at `0.1`. For deterministic validation keep `1.0`.
- Set access controls and retention policies for logs, metrics and traces separately.
- Before promotion run Java tests via `mvn -pl payment-platform,payment-mcp-server,ai-ops-agent -am test` in a Maven/Java 21 environment; note integration tests may need Docker/Testcontainers.
