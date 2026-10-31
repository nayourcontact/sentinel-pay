# SentinelPay — AI-Native Distributed Payment Operations Platform

**A production-inspired reference architecture for reliable payment processing, event-driven systems and secure AI-assisted operations.**

Built with **Java 21, Spring Boot 4, PostgreSQL, Kafka, Spring Security, Spring AI and Model Context Protocol (MCP)**. The payment core remains deterministic and authoritative; the AI agent can investigate operational state through **read-only, audited tools**, without direct database or payment-provider access.

> **Portfolio / educational project.** Uses a mock payment provider and local infrastructure. Not a certified payment processor and **not intended for real financial transactions**.

[![CI](https://github.com/nayourcontact/sentinel-pay/actions/workflows/ci.yml/badge.svg)](https://github.com/nayourcontact/sentinel-pay/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-007396)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4-6DB33F)](https://spring.io/projects/spring-boot)

## At a glance

| Engineering challenge | Approach demonstrated |
|---|---|
| Transactional consistency | Payment mutation and outbox event inserted in one PostgreSQL transaction |
| Reliable event processing | Kafka at-least-once publication, idempotent consumers and database uniqueness constraints |
| Duplicate requests / concurrent updates | Idempotency keys and optimistic locking |
| Secure service boundaries | Spring Security, JWT/RBAC, OAuth2 client credentials and internal API-key checks |
| AI-assisted incident analysis | Spring AI + Ollama, PGVector RAG and read-only MCP tools for live operational evidence |
| Operations & delivery | OpenTelemetry, Prometheus/Grafana, Docker Compose, CI/CD, performance tests and runbooks |

**Key design principle:** `RAG = documented knowledge` · `MCP = live system evidence` · `LLM = reasoning`; the LLM is **never** the source of truth for payment state.

## Quick start

Requirements: Java 21 and Maven for local builds; Docker Compose for the reference stack. Ollama models require additional disk space and memory.

```bash
git clone https://github.com/nayourcontact/sentinel-pay.git
cd sentinel-pay
cp .env.example .env
# Review local-only values in .env; never use sample secrets outside development.
docker compose up --build -d
docker compose exec ollama ollama pull qwen3:4b
docker compose exec ollama ollama pull nomic-embed-text
docker compose ps
```

On Windows PowerShell, use `Copy-Item .env.example .env` instead of `cp` if preferred. For a clean Maven verification run: `mvn clean verify`.

**Local entry points:** [Operations UI](http://localhost:3000) · [Payment API](http://localhost:8085) · [Agent](http://localhost:8081) · [Grafana](http://localhost:3001). These links work after starting the stack locally; they are **not hosted demos**.

### Explore the implementation

- **[Architecture and design trade-offs](docs/architecture.md)** — service boundaries, payment flow, consistency model.
- **[System design case study](docs/system-design-case-study.md)** — deeper engineering decisions and failure handling.
- **[JWT / OAuth2 / RBAC](docs/security-jwt-rbac.md)** — authentication, authorization and service-to-service access.
- **[OpenAPI / Swagger](docs/openapi-swagger.md)** — opt-in REST API documentation for Payment Platform and AI Ops Agent.
- **[Performance testing](docs/performance-test-report.md)** — methodology and test results.
- **[Observability and production hardening](docs/production-observability-hardening.md)** — telemetry and operations.
- **[CI/CD and deployment](docs/ci-cd-deployment.md)** — build and delivery workflow.
- **[Architecture decision records](docs/adr/)** · **[Runbooks](docs/runbooks/)** · **[Threat model](docs/threat-model/)**

### Security and API documentation

The REST APIs have JWT/RBAC security modes, with a separate internal operations key for the protected operations boundary. The MCP service uses OAuth2 client credentials to access the Payment Platform in the secured integration scenario. **Documentation does not grant access to business endpoints.**

Swagger/OpenAPI is **disabled by default** (`OPENAPI_ENABLED=false`). An isolated local test can enable it with `OPENAPI_ENABLED=true`, serving `/swagger-ui/index.html` and `/v3/api-docs` in the application. The Payment Platform Swagger page has also been tested with unauthenticated viewing in the local test configuration; this deliberately exposes API metadata and must **not** be enabled on a public production deployment. The AI Ops Agent Swagger route retains its independent security configuration and requires separate runtime validation.

### Contents

- [Runtime topology](#runtime-topology)
- [Architecture rule that matters](#architecture-rule-that-matters)
- [High-level architecture](#high-level-architecture)
- [MCP tools](#mcp-tools)
- [Failure semantics](#failure-semantics)
- [End-to-end payment flow](#end-to-end-payment-flow)
- [Start the platform — expanded instructions](#start-the-platform)
- [Create a payment](#create-a-payment)
- [Ask the AI operator](#ask-the-ai-operator)
- [Design decisions worth discussing](#design-decisions-worth-discussing)
- [Security notes](#security-notes)

---

## Runtime topology

| Runtime | Host Port | Responsibility |
|---|---:|---|
| `payment-platform` | 8085 | Authoritative payment domain, persistence, Kafka, outbox, projections |
| `ai-ops-agent` | 8081 | RAG + LLM reasoning + MCP client |
| `payment-mcp-server` | 8082 | Audited, narrow live-data tools for the agent |
| `ops-ui` | 3000 | Payment console + AI incident investigator |
| Prometheus | 9090 | Metrics |
| Grafana | 3001 | Dashboards |
| Ollama | internal 11434 | Local chat + embeddings |

Docker-internal ports may differ from host-exposed ports.

For example, `payment-platform` listens on port `8080` inside the Docker network and is exposed as `8085` on the host.

## Architecture rule that matters

```text
RAG = runbooks / policy / architecture knowledge
MCP = authoritative live payment data
LLM = reasoning over evidence
```

The designed agent trust boundary does **not** grant direct SQL, shell, filesystem, Kafka-admin or payment-provider credentials.

This separation is deliberate:

- the payment core remains deterministic and authoritative;
- live operational evidence comes through MCP;
- static operational knowledge comes through RAG;
- the LLM reasons over evidence instead of becoming the system of record.

## High-level architecture

```text
Client
  |
  v
Payment API
  |
  +---- PostgreSQL
  |       |
  |       +-- Payment
  |       +-- Outbox
  |
  v
Outbox Publisher
  |
  v
Kafka
  |
  +--> Payment Orchestrator
  |
  +--> Ledger Projection
  |
  +--> Webhook Projection


Operations / AI

User
  |
  v
AI Ops Agent
  |
  v
MCP Client
  |
  v
Payment MCP Server
  |
  v
Read-only Internal Ops API
  |
  v
Payment Platform
```

## MCP tools

Current read-only tools include:

- `get_payment`
- `get_payment_timeline`
- `get_failed_payments`
- `get_outbox_status`
- `get_ledger_entries`
- `get_webhook_queue`
- `get_incident_snapshot`

All current tools are read-only and idempotent.

Production-impacting actions should be added only behind explicit authorization, deterministic validation, audit logging, idempotency and human approval workflows.

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

Kafka may redeliver events, therefore consumers are designed to be idempotent and critical projections are additionally protected by database uniqueness constraints.

This is a more defensible enterprise design than pretending Kafka, PostgreSQL and an external payment provider share one global transaction.

## End-to-end payment flow

A typical successful payment flow:

```text
POST /api/payments
        |
        v
AUTHORIZATION_PENDING
        |
        v
transactional outbox
        |
        v
payment.authorization.requested
        |
        v
Kafka
        |
        v
Payment Orchestrator
        |
        v
Mock Payment Provider
        |
        v
AUTHORIZED
        |
        +--> payment.authorized
        |
        +--> Ledger Projection
                |
                v
        AUTHORIZATION 149.90 EUR
```

The AI operations agent can then investigate the same payment through MCP:

```text
User
 -> AI Ops Agent
 -> MCP
 -> get_payment
 -> get_payment_timeline
 -> get_ledger_entries
 -> authoritative live answer
```

## Start the platform

Copy the example environment configuration:

```bash
cp .env.example .env
```

Start the stack:

```bash
docker compose up --build -d
```

Pull the local models once Ollama is healthy:

```bash
docker compose exec ollama ollama pull qwen3:4b
docker compose exec ollama ollama pull nomic-embed-text
```

Verify that the services are running:

```bash
docker compose ps
```

> The default local setup uses `qwen3:4b` because larger local models such as `qwen3:8b` may require significantly more Docker / WSL memory when running on CPU.

Open:

- Ops Console: http://localhost:3000
- Payment API: http://localhost:8085
- Agent: http://localhost:8081
- Payment MCP: http://localhost:8082/mcp
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001

## Example environment configuration

A local `.env.example` can look like this:

```env
PAYMENT_OPS_INTERNAL_KEY=change-me-internal
AGENT_API_KEY=change-me-agent

OLLAMA_CHAT_MODEL=qwen3:4b
OLLAMA_EMBED_MODEL=nomic-embed-text
```

Do not commit real credentials or secrets.

> **Authentication note:** Examples below illustrate the default local/demo API-key mode. When JWT mode is enabled, supply valid bearer tokens with the required roles; internal operations additionally require the internal key. See [security documentation](docs/security-jwt-rbac.md).

## Create a payment

### Bash / Git Bash

```bash
curl -i -X POST http://localhost:8085/api/payments \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: order-2026-000042' \
  -d '{"amount":"149.90","currency":"EUR"}'
```

Repeat the request with the same idempotency key: it resolves to the existing payment rather than creating a duplicate.

### PowerShell

```powershell
$body = @{
    amount = "149.90"
    currency = "EUR"
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8085/api/payments" `
    -Headers @{ "Idempotency-Key" = "order-2026-000042" } `
    -ContentType "application/json" `
    -Body $body
```

## Inspect a payment through the internal operations API

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8085/internal/ops/payments/<PAYMENT_UUID>" `
    -Headers @{ "X-Internal-Ops-Key" = "change-me-internal" }
```

Timeline:

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8085/internal/ops/payments/<PAYMENT_UUID>/timeline" `
    -Headers @{ "X-Internal-Ops-Key" = "change-me-internal" } |
    ConvertTo-Json -Depth 10
```

Ledger:

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8085/internal/ops/ledger/<PAYMENT_UUID>" `
    -Headers @{ "X-Internal-Ops-Key" = "change-me-internal" } |
    ConvertTo-Json -Depth 10
```

## Ask the AI operator

Index the bundled operational runbooks first, if the repository includes the ingestion script:

```bash
./scripts/ingest-runbooks.sh
```

Then ask the protected agent endpoint.

### Bash / Git Bash

```bash
curl -X POST http://localhost:8081/api/v1/chat \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: change-me-agent' \
  -d '{"message":"Investigate current payment platform health. Use live tools before drawing conclusions."}'
```

### PowerShell

```powershell
$agentBody = @{
    message = "Investigate payment <PAYMENT_UUID>. Use live MCP tools. Tell me its current status, timeline, and ledger entries."
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8081/api/v1/chat" `
    -Headers @{ "X-API-Key" = "change-me-agent" } `
    -ContentType "application/json" `
    -Body $agentBody
```

Good demo questions:

```text
Investigate current payment platform health. Use live tools first.
Show recent failed payments and explain whether this looks like provider decline or platform failure.
Why did payment <UUID> fail? Inspect its timeline and ledger before answering.
Is the transactional outbox healthy right now?
```

## Local AI performance note

The local reference setup is optimized for reproducibility rather than low latency.

On CPU-only Docker / WSL environments, local LLM inference can be significantly slower than the MCP and payment backend calls themselves.

The important architectural distinction is:

```text
MCP tool latency        -> operational backend / network work
LLM latency             -> local inference / reasoning work
```

For production-style deployments, the LLM runtime could be moved to a GPU-backed or managed inference environment without changing the payment core or MCP trust boundary.

## Repository layout

```text
SentinelPay/
├── payment-platform/       # deterministic payment core
├── payment-mcp-server/     # governed AI/live-data boundary
├── ai-ops-agent/           # Spring AI + Ollama + PGVector
├── ops-ui/                 # React operations console
├── infra/                  # Prometheus + Grafana
├── load-tests/             # k6, if present
├── docs/
│   ├── adr/
│   ├── runbooks/
│   ├── threat-model/
│   └── architecture.md
├── docker-compose.yml
└── pom.xml
```

## Design decisions worth discussing

1. Why transactional outbox is used instead of a DB commit followed by a Kafka send.
2. Why the platform uses at-least-once delivery with idempotent consumers instead of relying on distributed exactly-once guarantees.
3. Why database uniqueness constraints are used as the final duplicate-processing barrier.
4. Why the authoritative payment core is separated from probabilistic AI reasoning.
5. Why the AI agent cannot query PostgreSQL directly.
6. Why RAG and MCP solve different problems.
7. What happens after a duplicate Kafka delivery.
8. What happens if the application dies after the DB commit but before Kafka publication.
9. Why future AI write tools require authorization, deterministic validation, audit logging, idempotency and human approval.
10. Why the application runtimes represent meaningful trust and responsibility boundaries rather than arbitrary service decomposition.
11. How provider routing, reconciliation, tracing and production deployment could evolve further.

See `docs/architecture.md`, `docs/adr/` and `docs/runbooks/` for the reasoning behind the implementation, if those documents are present in the repository.

## Security notes

- Never commit `.env` files containing real credentials.
- Keep `.env` in `.gitignore`.
- Provide only safe placeholders in `.env.example`.
- The AI agent must not receive direct database, shell, Kafka-admin or payment-provider credentials.
- Current MCP operations are intentionally read-only.

## Disclaimer

SentinelPay is a portfolio / reference architecture project intended to demonstrate backend engineering, distributed-system design and governed AI integration.

It is not a production payment processor and should not be used to process real financial transactions.


Observability production hardening and validation: [docs/production-observability-hardening.md](docs/production-observability-hardening.md).

## OpenTelemetry release readiness

Sampling and Tempo retention are configurable via `TRACING_SAMPLING_PROBABILITY` and `TEMPO_BLOCK_RETENTION` (defaults `1.0`, `48h`). See [production readiness, architecture and verification](docs/distributed-tracing-production-readiness.md). The offline Tempo export verifier can enforce HTTP, Kafka or outbox span relationships via `--require http|kafka|outbox`; run its Python regression suite with `python -m unittest discover -s scripts/tests -v`.
