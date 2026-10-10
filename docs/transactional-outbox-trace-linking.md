# Transactional outbox — OpenTelemetry span linking

## Scope and reasoning

Outbox rows are committed in the same transaction as their payment changes. The scheduler publishes rows later; therefore a **span link** (not a parent-child relationship) represents the causal relation correctly. The Kafka producer and consumers keep their existing parent-child relationships.

## Changed components

- `V2__outbox_trace_link.sql` introduces nullable `origin_trace_id` (32 chars), `origin_span_id` (16 chars) and `origin_trace_flags` (2 chars). No data rewrite. Previously persisted rows remain publishable without links.
- `JpaOutbox.append` records the currently active OpenTelemetry span identifiers (no baggage, no payload changes).
- `OutboxPublishingService.publishWithTraceLink` creates a **new root span** named `outbox.publish.event`, adds a link to the persisted originating context, and publishes to Kafka while this span is current. Kafka producer and consumer observations should descend from this span. Invalid/missing metadata simply produces no link.
- `payment-mcp-server/config/RestClientConfig.java` restores the observation-aware `RestClient.Builder` the user's local system already needed. The previously distributed snapshot did not contain it.

Note: Grafana Tempo does **not** necessarily display span links as joined trace trees. Open the `outbox.publish.event` span and inspect **Links** / JSON export for the originating `traceId` + `spanId`. Those are two separate Trace IDs by design.

## Database deployment / checks

Existing Flyway V1 is unchanged. Before applying, review schema history:

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history ORDER BY installed_rank;
SELECT column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_schema='public' AND table_name='outbox_events'
  AND column_name LIKE 'origin_%' ORDER BY column_name;
```

Then build and launch (Flyway applies V2 on startup):

```powershell
docker compose build payment-platform payment-mcp-server
docker compose up -d --no-deps payment-platform payment-mcp-server
docker compose ps payment-platform payment-mcp-server
# MCP server restart invalidates session; restart the agent AFTER MCP is healthy
docker compose restart ai-ops-agent
```

Post-check SQL:

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history WHERE version = '2';
SELECT id, event_type, origin_trace_id, origin_span_id, origin_trace_flags, created_at, published_at
FROM outbox_events ORDER BY created_at DESC LIMIT 10;
```

## Validation

1. POST a new payment with a new `Idempotency-Key`. Preserve the HTTP request's Trace ID from Tempo.
2. Wait for authorization, then query the SQL above. Expect a newly created outbox record with `origin_trace_id` and `origin_span_id` populated. (Rows written without an active sampled trace may have null metadata.)
3. In Tempo search for `{ resource.service.name = "sentinelpay-payment-platform" && name = "outbox.publish.event" }` around the event time.
4. Export this trace as JSON. The `outbox.publish.event` span must have a `links` array containing the originating HTTP trace ID and span ID. The Kafka `payment.authorized send` span and downstream consumers should be under the new outbox root.
5. Confirm no payment details / prompts / tokens leaked into the new attributes; only outbox event ID/type are added.
6. Observe retry behavior. Publication failure should increment `attempts` and may retry, creating a new publish span with the same original link. The design does **not** change the project's existing at-least-once semantics.

## Operational notes

- The previous approach of forcibly continuing the HTTP parent trace across scheduler ticks is intentionally not used.
- Trace links are only preserved for newly recorded outbox rows. No automatic backfill for historical events.
- The original project handles Kafka `send().get()` in a DB transaction and already has retry / duplicate-processing considerations. This change does not solve Kafka/DB atomicity or deduplication.
- Use environment-specific sampling in production and configure retention / access restrictions for tracing data. The existing Spring AI `db.vector.query.content` attribute contains full user prompts and should be separately sanitized/disabled before production use.
- Rollback the Java change without dropping columns (safe expand/contract). Dropping V2 columns requires explicit DBA-reviewed migration, not manual modification of Flyway history.
- This package was statically reviewed, but **Maven tests and Docker runtime were not run in this environment**. Verify on the user's machine before deployment.
