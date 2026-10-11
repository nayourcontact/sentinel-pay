# ADR 0007 — Bounded retries only on read-only MCP upstream operations

**Status:** Accepted as current implementation (documented 2026-10-11)

## Context
MCP diagnostic reads depend on the Payment Operations HTTP API. Transient network failures should not cause unbounded stalls. Retrying a payment authorization after timeout can double-charge when a PSP's result is unknown.

## Decision
Protect read-only MCP upstream calls with explicit connect/read timeouts, Resilience4j retry and circuit breaker. Preserve the payment core as authoritative. Do not apply automatic write retries to payment-provider authorization without end-to-end PSP idempotency/reconciliation guarantees.

## Alternatives
- Retry all HTTP calls: rejected due to payment side effects.
- No resilience: rejected for avoidable transient read failures.

## Consequences / verification
Read-side diagnostics can fail fast during upstream outages; some errors still propagate. Test timeout, retry budget, 4xx exclusion and breaker reopening/half-open recovery. See `docs/resilience4j-retry-circuit-breaker-timeout.md`.
