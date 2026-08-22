# SentinelPay — AI-Native Distributed Payment Operations Platform

A senior-level Java portfolio project that combines reliable payment processing with a governed AI operations agent.

**Core idea:** the payment system remains deterministic and authoritative; the AI layer can investigate it, but cannot bypass its security or consistency boundaries.

## What this demonstrates

- Java 21 and Spring Boot 4
- domain-centric payment state machine
- PostgreSQL + Flyway
- transactional outbox
- Kafka and at-least-once delivery
- idempotent ledger and webhook projections
- optimistic locking and request idempotency
- Spring AI + Ollama
- MCP tool calling for live system evidence
- PGVector RAG for runbooks and policy
- prompt-injection-aware trust boundaries
- human-in-the-loop design for future write operations
- Prometheus / Grafana observability
- React + TypeScript operations console
- Docker Compose and GitHub Actions
- architecture decision records and incident runbooks

## Runtime topology

| Runtime | Port | Responsibility |
|---|---:|---|
| `payment-platform` | 8080 | Authoritative payment domain, persistence, Kafka, outbox, projections |
| `ai-ops-agent` | 8081 | RAG + LLM reasoning + MCP client |
| `payment-mcp-server` | 8082 | Audited, narrow live-data tools for the agent |
| `ops-ui` | 3000 | Payment console + AI incident investigator |
| Prometheus | 9090 | Metrics |
| Grafana | 3001 | Dashboards |
| Ollama | 11434 | Local chat + embeddings |

## Architecture rule that matters

```text
RAG = runbooks / policy / architecture knowledge
MCP = authoritative live payment data
LLM = reasoning over evidence
```

The agent has **no direct SQL, shell, filesystem, Kafka-admin or provider credentials**.

## MCP tools

- `get_payment`
- `get_payment_timeline`
- `get_failed_payments`
- `get_outbox_status`
- `get_ledger_entries`
- `get_webhook_queue`
- `get_incident_snapshot`

All current tools are read-only and idempotent. This is deliberate. Production-impacting actions should be added only behind explicit authorization and approval workflows.

## Start the platform

```bash
cp .env.example .env
docker compose up --build -d
```

Pull the local models once Ollama is healthy:

```bash
docker compose exec ollama ollama pull qwen3:8b
docker compose exec ollama ollama pull nomic-embed-text
```

Open:

- Ops Console: http://localhost:3000
- Payment API: http://localhost:8080
- Agent: http://localhost:8081
- Payment MCP: http://localhost:8082/mcp
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001

## Create a payment

```bash
curl -i -X POST http://localhost:8080/api/payments \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: order-2026-000042' \
  -d '{"amount":"149.90","currency":"EUR"}'
```

Repeat the request with the same idempotency key: it resolves to the existing payment rather than creating a duplicate.

## Ask the AI operator

Index the bundled operational runbooks first:

```bash
./scripts/ingest-runbooks.sh
```

Then ask the protected agent endpoint:

```bash
curl -X POST http://localhost:8081/api/v1/chat \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: dev-secret-change-me' \
  -d '{"message":"Investigate current payment platform health. Use live tools before drawing conclusions."}'
```

Good demo questions:

```text
Investigate current payment platform health. Use live tools first.
Show recent failed payments and explain whether this looks like provider decline or platform failure.
Why did payment <UUID> fail? Inspect its timeline and ledger before answering.
Is the transactional outbox healthy right now?
```

## Failure semantics

The platform intentionally does **not** claim distributed exactly-once processing.

```text
PostgreSQL transaction
  payment mutation
  + outbox insert
        |
        v
at-least-once Kafka publication
        |
        v
idempotent consumers / unique projection keys
```

That is a more defensible enterprise design than pretending Kafka, PostgreSQL and an external PSP share one global transaction.

## Repository layout

```text
SentinelPay/
├── payment-platform/       # deterministic payment core
├── payment-mcp-server/     # governed AI/live-data boundary
├── ai-ops-agent/           # Spring AI + Ollama + PGVector
├── ops-ui/                 # React operations console
├── infra/                  # Prometheus + Grafana
├── load-tests/             # k6
├── docs/
│   ├── adr/
│   ├── runbooks/
│   ├── threat-model/
│   └── architecture.md
├── docker-compose.yml
└── pom.xml
```

## Interview talking points

Be prepared to explain these decisions rather than memorizing code:

1. Why transactional outbox instead of DB commit followed by a Kafka send.
2. Why at-least-once + idempotency instead of a vague exactly-once claim.
3. Why the AI agent cannot query PostgreSQL directly.
4. Why RAG and MCP solve different problems.
5. What happens after a duplicate Kafka delivery.
6. What happens if the app dies after the DB commit but before publication.
7. Why an AI write tool needs human approval, deterministic validation, audit and idempotency.
8. Why three runtimes are justified trust boundaries rather than microservice theatre.
9. How you would evolve provider routing, reconciliation, tracing and Kubernetes deployment in production.

See `docs/architecture.md`, `docs/adr/` and `docs/runbooks/` for the reasoning behind the implementation.
