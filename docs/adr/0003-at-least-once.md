# ADR 0003: Prefer at-least-once delivery plus business idempotency

## Status
Accepted

## Context
Messaging platforms can provide transactional features, but an end-to-end business process also touches external payment providers and PostgreSQL. Claiming global exactly-once behavior would be misleading.

## Decision
Assume messages may be delivered more than once. Protect side effects using aggregate state checks, business uniqueness constraints and deterministic event identity.

## Consequences
- Replays are safe by design.
- Failure recovery is explicit and testable.
- External provider calls require provider-level idempotency keys in a real PSP adapter.
