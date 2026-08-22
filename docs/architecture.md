# SentinelPay Architecture

SentinelPay is a portfolio-grade reference architecture for AI-assisted payment operations. It intentionally separates authoritative transaction processing from probabilistic AI reasoning.

```text
Merchant / Ops UI
       |
       v
+---------------------+       Kafka       +---------------------+
| Payment Platform    |------------------>| Ledger / Webhook    |
| Spring Boot + PG    |                   | projections         |
+----------+----------+                   +---------------------+
           |
           | narrow internal read API
           v
+---------------------+       MCP        +---------------------+
| Payment MCP Server  |<---------------->| AI Ops Agent        |
| audited tools       |                  | Spring AI + Ollama  |
+---------------------+                  +----------+----------+
                                                   |
                                                   v
                                             PGVector RAG
                                             runbooks/policy
```

## Trust boundaries

- Payment Platform owns authoritative transaction state.
- Payment MCP Server exposes a deliberately narrow, read-only operations surface.
- AI Ops Agent never receives SQL, filesystem, shell or Kafka-admin access.
- RAG contains documentation, not live transaction truth.
- Any future production-impacting tool must use explicit authorization, human approval, audit persistence and replay-safe command semantics.

## Consistency model

Payment state and outbox event are committed atomically in PostgreSQL. Kafka delivery is at-least-once. Consumers protect projections through idempotency constraints. End-to-end exactly-once is not claimed across PostgreSQL, Kafka and external providers.

## Why a modular monorepo

The repository has three Java runtimes because they represent different trust and scaling boundaries, not because microservices are inherently better. The payment core remains independent from the AI runtime and can operate without it.
