# Ticket 6 — SentinelPay load and performance testing

## Verified interface

- `POST /api/payments` takes `{ "amount": "10.00", "currency": "HUF" }` and a required `Idempotency-Key` header; successful creation returns HTTP 201 and payment `id`.
- `GET /api/payments/{id}` returns a payment view.
- Default local Docker Compose port for Payment Platform: `localhost:8085`.

## Safety and isolation

**DO NOT** run write or race mode against the retained `payments` database. These modes create payment and outbox records. Use a separate, disposable database/stack first, with test-only credentials and payment provider. The script's `-IsolatedDatabaseConfirmed` flag is a user assertion, *not automatic verification of DB isolation*. The runner refuses non-loopback endpoints, but localhost can still map to a real database; check Docker configuration.

Read mode also requires an existing disposable test payment UUID; do not use production identifiers. No automated cleanup is attempted. Provider and Kafka effects should be controlled in a test environment.

## Install

On Windows: `winget install k6.k6`, restart shell, `k6 version`. The scripts use `jslib.k6.io` for UUIDs (network access at k6 module-load time); vendor this dependency if running offline.

## Run (PowerShell, repository root)

```powershell
.\scripts\run-performance.ps1 -Mode read -PaymentId '<UUID-of-disposable-test-payment>'
.\scripts\run-performance.ps1 -Mode write -IsolatedDatabaseConfirmed
.\scripts\run-performance.ps1 -Mode race -IsolatedDatabaseConfirmed
```

Read mode: 5 requests/s for 30 seconds; write mode: 1 request/s for 30 seconds. Both measure request failures and p95 latency. Race mode fires five concurrent POSTs using the same new idempotency key. Expected: all HTTP 201 and identical `id`; a race failure is **not** to be ignored. The current `PaymentApplicationService.create` has check-then-insert behavior; DB uniqueness handling and request concurrency must be validated from actual results.

Default experimental thresholds: failed requests <1%, p95 <1000ms, zero dropped iterations; these are demonstration thresholds, **not** approved production SLOs. Since the provider processing is async, HTTP POST latency does not measure end-to-end settlement latency.

## Interpretation and evidence

Capture k6 console output, JSON summary in `performance/results/`, Docker CPU/memory and PostgreSQL metrics, test machine specs, Git commit SHA, service version, date, and whether an isolated database was used. `performance/results/` is ignored by Git. Fill in `docs/performance-test-report.md` with observed p50/p95/p99 and actual achieved throughput (if recorded). Do not claim production readiness from a local benchmark. Verify that idempotency produces no duplicate ledger side effects with separate DB checks when testing end-to-end operations.
