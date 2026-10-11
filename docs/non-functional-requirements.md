# SentinelPay — Non-functional requirements (Ticket 4)

## Classification

**Observed** = verified from source/configuration; **proposed** = engineering acceptance target, **not** a measurement or contractual SLO. Baselines must be measured before making availability/latency promises. Single-host Compose is demo/development, not HA production.

| ID | Quality attribute | Proposed acceptance / measurement | Evidence / limitation | Verification |
|---|---|---|---|---|
| NFR-01 | Payment integrity | No partial payment/outbox commit; duplicated client request must not create duplicated business payment | Atomic outbox and idempotency patterns described in code and ADRs | Unit + PostgreSQL Testcontainers rollback and concurrent idempotency tests |
| NFR-02 | Event delivery | At-least-once semantics explicitly allowed; duplicate events must not double-create ledger/webhook projection | Kafka, unique constraints and idempotent projections | Publish same message twice; assert one side effect |
| NFR-03 | Read-side availability | AI/MCP outage shall not prevent core payment processing | Independent services in Compose | Stop agent/MCP, create/query payment directly |
| NFR-04 | External HTTP isolation | Connect/read timeouts and bounded retry on read-only MCP Ops calls; avoid automatic retry on non-idempotent PSP writes | Resilience4j implementation documented under `docs/resilience4j-retry-circuit-breaker-timeout.md` | Inject 5xx, 429, timeout, CB OPEN; assert request budget |
| NFR-05 | Authentication | Missing/invalid API keys denied on protected operations endpoints; mutating tools not exposed by read-only MCP interface | Source authorization and prior hardening ticket | Negative auth tests; inspect routes/proxy exposure |
| NFR-06 | Secrets/privacy | No real secrets committed; redact API keys, payment PII and prompts in logs/traces | Local Compose has explicit dev defaults; production overlay requires env values | Secret scanner and telemetry sample review |
| NFR-07 | Schema evolution | Existing Flyway V1–V3 checksums preserved; new DB can initialize via B3 and future V4+ | Baseline migration and legacy notes | Fresh Testcontainers + staging history/validate check |
| NFR-08 | Observability | Correlate HTTP request, outbox event and Kafka processing; collect error counts and backlog | OTLP/Tempo, Prometheus/Grafana configured | Trace and metrics smoke test; inspect sensitive attributes |
| NFR-09 | CI / reproducibility | Main branch passes Maven verify and UI build before immutable SHA-tag image publication | `.github/workflows/ci.yml` | GitHub Actions run, GHCR tags, rollback rehearsal |
| NFR-10 | Recovery | Recover Kafka/outbox from outage without manual DB edits or duplicate effects | Outbox runbook / at-least-once model | Restart Kafka; replay; verify published+pending counts |
| NFR-11 | Availability capacity | **TBD:** business SLO and concurrency target; not inferable from source | No measured load baseline attached | k6 load profile and p95/p99 + DB/Kafka capacity data |
| NFR-12 | RPO/RTO | **TBD:** DB backup schedule and recovery targets, not established by Compose volumes | Named volumes are not backups | Backup/restore drill and documented RPO/RTO |

## Proposed local acceptance checks (not production SLOs)

- Critical path: `mvn -pl payment-platform,payment-mcp-server,ai-ops-agent -am clean verify` on host with Docker/Testcontainers. Record success and report tests, not just compile.
- Fault injection: shut down Kafka temporarily; payment state/outbox persistence must remain consistent and pending messages replay after broker recovery. This is a test hypothesis to verify, not proof it presently passes.
- AI isolation: stop Ollama, agent and MCP one at a time. Core payment APIs should continue to serve; diagnostic capabilities may degrade with explicit errors.
- Management endpoints: verify which routes are reachable from localhost, Docker network and outside host. Production requires protected ingress and validated secrets.
- Traces/metrics: check `traceId` continuity and confirm tokens/keys, request body and private payment data are absent.

## Production readiness gaps requiring a separate deployment decision

No HA or replicated PostgreSQL/Kafka, no verified disaster recovery, no measured SLOs, no proven external PSP idempotency contract, no complete SCA evidence, and no authenticated public ingress established by this document. These are **open risks**, not work implicitly implemented by Ticket 4.
