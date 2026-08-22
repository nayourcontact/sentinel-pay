# SentinelPay Payment Operations Runbook

## Evidence hierarchy
Live MCP tool output is authoritative for transaction state. RAG documents describe policy and response procedure only.

## Payment failure investigation
1. Inspect the incident snapshot.
2. Inspect recent failed payments.
3. For a specific payment, inspect its full timeline and ledger entries.
4. Distinguish provider decline from platform delivery failure.
5. Check transactional outbox state before blaming Kafka or downstream projections.

## Transactional outbox
A payment state transition and its integration event are persisted in one database transaction. Events are published asynchronously. At-least-once delivery is expected; consumers must be idempotent.

Escalate if failed outbox events are non-zero or pending events continuously grow. A transient pending count is normal.

## Provider degradation
A provider decline is a business response, not necessarily a platform incident. Escalate provider degradation when failures materially exceed the established baseline and infrastructure evidence does not indicate an internal platform failure.

## Webhooks
Webhook projection is intentionally idempotent using Kafka topic/partition/offset identity. Pending deliveries should be investigated separately from payment authorization state.

## Human-in-the-loop rule
The AI agent may diagnose and recommend remediation. Production-impacting writes such as refunds, traffic shifting, replay or provider switching require explicit human approval and a separately authorized write tool. Read-only tools must never be treated as write authority.
