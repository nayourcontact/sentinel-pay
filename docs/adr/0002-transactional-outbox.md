# ADR 0002: Use a transactional outbox

## Status
Accepted

## Context
Writing a payment to PostgreSQL and publishing a Kafka event are two independent side effects. Performing them sequentially introduces a crash window that can lose events or publish events for rolled-back state.

## Decision
Persist domain state and an outbox row in the same database transaction. A separate publisher locks unpublished rows using PostgreSQL `FOR UPDATE SKIP LOCKED`, publishes them, and records publication time.

## Consequences
- Business state and intent-to-publish are atomic.
- Publication is at-least-once, so all consumers must be idempotent.
- The outbox table needs retention/archival in production.
