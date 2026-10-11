# Task 1 — Resilience4j: retry, circuit breaker, timeout

## Scope and analysis

- Java 21 / Spring Boot 4.1.0 / Maven multimodule project.
- `payment-platform` uses a deterministic local `PaymentProvider`; it has no network I/O today. `AuthorizationProcessor` executes authorization while a DB transaction is open and persists payment state and an outbox event.
- `payment-mcp-server` uses `PaymentOperationsClient` to call the internal read-only Operations HTTP API, previously without bounded HTTP timeouts, retries or a circuit breaker.
- Payment writes must not be retried speculatively: a timed-out authorization can still have reached the PSP. PSP-side idempotency and reconciliation are prerequisites for changing that flow.

## Implementation

Changed in `payment-mcp-server`:

1. Added Resilience4j 2.3.0 `resilience4j-retry` and `resilience4j-circuitbreaker` core modules. Using a programmatic decorator avoids dependence on an unverified Spring Boot 4 AOP starter combination.
2. `RestClientConfig`: Java JDK HTTP client with connect timeout (default 1s) and request read timeout (default 2s). **Read timeout is not a guaranteed hard overall deadline**; slow streaming or other operations may last longer. For strict end-to-end deadlines, a further design is necessary.
3. `PaymentOperationsClient`: per-client circuit breaker and retry over GET requests only. Up to 3 total attempts including the first. Retryable errors: network I/O / read timeout (`ResourceAccessException`), HTTP 5xx, HTTP 429. No retry of other 4xx, deserialization errors or business failures.
4. Breaker: 10-call count window, minimum 5 calls, 50% failure threshold, 10 seconds OPEN. Retry wraps breaker; individual attempts count toward breaker metrics. A call during OPEN fails immediately with `CallNotPermittedException`; errors remain visible to caller, no fabricated operational data is returned.
5. Configuration is exposed via environment variables in `payment-mcp-server/src/main/resources/application.yml`.

## Automated tests included

`PaymentOperationsClientTest` covers:

- successful GET and internal authorization header;
- transient HTTP 500 retry and recovery;
- HTTP 400 does not retry;
- repeated HTTP 500 opens the breaker, with fast rejection;
- retryable vs permanent exception classification.

Run from repository root:

```bash
mvn -pl payment-mcp-server -am test
```

**Verification status:** Java 21 is available, XML POMs were parsed successfully, static checks for resilience decorators and request timeout configuration passed. Maven is not installed in the available execution environment and Maven Central could not be reached; **tests have been authored but not executed**, and Java compile/CI green status is not claimed.

## Configuration

| Environment variable | Default | Meaning |
|---|---|---|
| `PAYMENT_API_CONNECT_TIMEOUT` | `1s` | TCP connect timeout |
| `PAYMENT_API_READ_TIMEOUT` | `2s` | HTTP request read timeout |
| `PAYMENT_API_RETRY_MAX_ATTEMPTS` | `3` | Total attempts, including first |
| `PAYMENT_API_RETRY_WAIT` | `200ms` | Wait between retries |
| `PAYMENT_API_CB_FAILURE_THRESHOLD` | `50` | Failure percentage |
| `PAYMENT_API_CB_MINIMUM_CALLS` | `5` | Minimum calls for evaluation |
| `PAYMENT_API_CB_WINDOW_SIZE` | `10` | Sliding count window |
| `PAYMENT_API_CB_OPEN_DURATION` | `10s` | OPEN duration before HALF_OPEN |

## Definition of Done

- [x] Inspect payment and MCP architecture and locate read-only integration boundary
- [x] Introduce retry and circuit breaker on read-only HTTP calls
- [x] Add bounded HTTP connect/read timeouts
- [x] Exclude permanent failures from retry
- [x] Preserve payment write contract and state handling
- [x] Add deterministic unit tests using Spring mock HTTP server
- [x] Document configuration and rollback
- [ ] Run Maven compile and JUnit suite on a machine with Maven/dependencies
- [ ] Validate a real slow/stalled upstream via integration test, including recovery after OPEN
- [ ] Instrument/alert breaker and retry metrics (recommended for production)

## Operations, rollout and rollback

1. Build/test only the MCP module and its required reactor dependencies. Run canary with a deliberately degraded read-only Operations API. Verify 500/429 retry behavior and fast circuit OPEN. Check 400/401/403 are not retried.
2. Verify no fabricated data is sent to MCP tools on upstream failure. Check trace IDs and timing for the calls. Preserve upstream auth header confidentiality.
3. Watch latency and error rate after rollout. Each failing GET can generate up to three upstream calls, until the breaker opens.
4. Rollback: redeploy previous MCP module artifact. No DB changes and no event/message schema changes are involved.

## Risks / deferred changes

- Payment authorization has no real PSP HTTP transport yet; putting retry on the mock would falsely imply production safety. Do not retry PSP writes without a stable PSP idempotency key and unknown-outcome reconciliation.
- This feature does not make synchronous call cancellation guaranteed and does not introduce an absolute end-to-end timeout.
- The new breaker is per MCP instance and not shared between replicas.
- Secrets in local `.env` are deliberately excluded from the delivery ZIP.
