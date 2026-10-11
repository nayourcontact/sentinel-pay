# Ticket 5 — Monitoring, SLI/SLO and verification

## Verified architecture (repository snapshot 2026-10-11)

- `infra/prometheus/prometheus.yml` scrapes `/actuator/prometheus` every **5 seconds** from `payment-platform:8080`, `payment-mcp-server:8082`, and `ai-ops-agent:8081`.
- `infra/grafana/provisioning/datasources/datasource.yml` provisions Prometheus and Tempo. All three service configs export OTLP traces to Tempo (runtime environment supplied by Compose); OTLP metrics export is disabled, since Prometheus scrapes metrics.
- The payment platform exposes `health`, `info`, `prometheus`, `metrics` under Actuator in application config. This is **configuration evidence**, not verification that endpoints are reachable or secure from every network.
- The Compose deployment is single-host development/demo, not HA. No measured availability or throughput SLO is available.

## Proposed indicators (SLIs) and targets — NOT achieved SLOs

| Indicator | Definition | Suggested starting target | Source and caveat |
|---|---|---|---|
| Payment API availability | Successful/eligible requests to `POST /api/payments`, `GET /api/payments/{id}`; exclude expected client 4xx from server-failure numerator, track separately | Proposed >=99.5% over rolling 7 days in demo/test; **not production commitment** | Spring HTTP server request metrics; inspect actual `http.server.requests` labels before enabling alert |
| Payment API latency | p95 of completed payment API server HTTP requests | Proposed p95 <500 ms for read, <1 s for create under **defined** local load | Requires histogram SLO buckets (default Micrometer timers may not expose histogram buckets) and k6 verification; not currently measured |
| Dependency availability | Prometheus `up` for three backend scrape targets | 100% during a 10-minute local smoke-test run | `up{job=...}` measures scrape reachability, not business health |
| Outbox publishing lag | Time from outbox record creation to publish success; pending age | Establish baseline first; alert threshold TBD | Current `GET /internal/ops/outbox/status` is authoritative Ops data; metric availability/labels must be checked before writing rules |
| Event processing quality | DLQ/retry counts, duplicate-projection outcomes, consumer lag | 0 duplicate ledger effects; lag targets TBD | Integration tests and live Kafka metrics; no guaranteed exported series documented |
| Trace continuity | HTTP request ↔ outbox ↔ Kafka trace correlation | Representative trace manually verified | Tempo UI and logs; no claim of universal correlation |

**Important:** Prometheus HTTP timer metric names, route labels, and histogram series must be observed before using production PromQL for latency/error SLOs. No fabricated recording rules or alert thresholds are installed by this ticket.

## Useful PromQL (immediately supported by Prometheus itself)

```promql
up{job=~"payment-platform|payment-mcp-server|ai-ops-agent"}
```

```promql
count(up{job=~"payment-platform|payment-mcp-server|ai-ops-agent"} == 1)
```

Expected healthy target count is three. To inspect service-specific HTTP metric names after generating requests, explore `http_server_requests_seconds_count` / `http_server_requests_seconds_bucket` in Prometheus; only use those if they actually exist.

## Local smoke verification

1. `docker compose ps` — verify dependencies are running and healthy.
2. Run `powershell -ExecutionPolicy Bypass -File scripts/verify-observability.ps1` from the repository root.
3. Open Grafana at `http://localhost:3001` and verify Prometheus/Tempo provisioned data sources; inspect a payment trace if sample traffic exists.
4. Confirm no API keys, customer payment payloads or prompts are exposed in example spans/logs.
5. Record date, commit SHA, service versions, Prometheus query results and screenshots in a ticket comment. **Do not substitute source checks for runtime evidence.**

### Operational and security constraints

Metrics and health exposure needs ingress/network access control; local Compose binds Prometheus/agent/MCP ports to host interfaces unless production overlay applied. Never publish Actuator, Prometheus, Tempo, Grafana or MCP ports publicly without review. Trace sampling is configurable (`TRACING_SAMPLING_PROBABILITY`); 100% development sampling is not a production policy. No additional metrics/exporter or dependencies were introduced.

## Acceptance / rollback

- [x] Document verified existing telemetry architecture and the SLI specification.
- [x] Supply repeatable read-only smoke checker.
- [ ] Runtime checker succeeds on developer Docker host.
- [ ] Review one representative trace for correct linkage and redaction.
- [ ] Capture latency/error histogram availability and load baseline before claiming numerical SLOs.

Documentation and script only. Rollback: revert these files. See `docs/non-functional-requirements.md` and `docs/production-observability-hardening.md`.
