# Ticket 6 — Performance test report

**Status:** NOT RUN. Complete this report only with measured values.

| Field | Actual value |
|---|---|
| Git SHA / branch | Pending |
| Date / tester | Pending |
| Test environment and isolation | Pending |
| CPU / RAM / Docker resources | Pending |
| Payment API version | Pending |
| Test data / payment UUID | Pending (avoid customer IDs) |

| Test | Target rate | Actual RPS | p50 | p95 | p99 | Failure rate | Result |
|---|---:|---:|---:|---:|---:|---:|---|
| GET payment | 5/s | — | — | — | — | — | NOT RUN |
| POST payment | 1/s | — | — | — | — | — | NOT RUN |
| Concurrent idempotency | 5 POST | — | — | — | — | — | NOT RUN |

## Findings

- Bottlenecks: not measured.
- End-to-end ledger / outbox consistency: not yet verified.
- CI evidence: not yet collected.
- Regression notes and follow-ups: pending.

## Acceptance criteria

- [ ] Isolated, disposable payment DB confirmed before writes
- [ ] GET load test executed and report filled in
- [ ] POST load test executed and report filled in
- [ ] Concurrent idempotency test executed and result analyzed
- [ ] p50 / p95 / p99 / throughput / error rate recorded from actual output
- [ ] Any correctness failures investigated rather than suppressed
