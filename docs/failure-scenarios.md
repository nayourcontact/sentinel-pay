# SentinelPay — Failure scenarios and recovery matrix (Ticket 4)

This is a **scenario plan** grounded in existing code and Compose configuration. Expected behavior is a testable requirement, not a claim that all chaos tests were executed.

| Failure / trigger | Expected system behavior | Detection | Verification / recovery | Risk / owner |
|---|---|---|---|---|
| Payment PostgreSQL unavailable | New payment writes fail; do not acknowledge persistence success; no synthetic outbox emission | API 5xx, datasource errors, failed DB health | Stop `payment-db` in isolated environment, call API; restore DB and check invariants | High; backend/DBA |
| Kafka temporarily unavailable | Payment and outbox committed together when DB available; publisher retries later; no lost event | Outbox pending-age/count, Kafka producer errors | Stop `kafka` **only in test stack**, create payment, restore and verify eventual publication | High; backend/ops |
| Duplicate Kafka delivery | Consumers ignore already-applied business effect; no duplicate ledger | Unique-key violations/idempotency counters | Replay duplicate event on isolated broker; inspect projection row counts | High; backend |
| Publisher crashes after Kafka ack before DB mark | Same outbox event can publish twice after restart; downstream remains idempotent | Pending/published gap and duplicate delivery | Inject failure at boundary in test; restart and inspect downstream unique keys | High; backend |
| Provider times out after unknown outcome | Do not blindly retry a charge; reconcile with PSP using stable idempotency key before final status | Timeout, unknown-outcome alert | Requires **future real PSP** contract test; mock is not evidence | Critical; payments |
| MCP Ops API 503 / network timeout | Bounded Resilience4j retries and circuit breaker; explicit degraded diagnostic result, core payment unaffected | MCP failure/retry/CB metrics, HTTP 5xx | Stub 503/latency and verify bounded attempts + fail-fast | Medium; agent/backend |
| MCP invalid internal API key | Deny access; do not leak diagnostics or secret value | Auth rejection log/counter without key | Call with missing/invalid credentials | High; security |
| Ollama unavailable or slow | AI response unavailable/degraded; platform payment processing independent | Agent response latency/error, Ollama health | Stop `ollama` in test Compose; verify payment APIs and failures | Medium; AI ops |
| PGVector agent DB down | Retrieval and AI ops impacted; payment DB unaffected | Agent datasource health/error | Stop `agent-db`, verify payment API, restart and inspect RAG | Medium; AI ops |
| Prompt injection in retrieved runbook | Retrieved text treated as data; no privileged shell, SQL or mutating payment tool | Tool invocation audits; injection evaluation | Insert malicious test runbook in isolated RAG and assert denied write capability | High; security/AI |
| Flyway init on fresh DB | Baseline B3 builds expected schema; no V1/V2 duplicate-table error | Startup failure, Flyway validate | Fresh PostgreSQL Testcontainers and schema assertions | High; backend |
| Flyway legacy DB upgrade | Historical checksums and ledger rows unchanged; startup validates | Flyway checksum validation | Restore anonymized backup/staging; validate before deploy | Critical; backend/DBA |
| Docker host / named volume lost | Entire local demo unavailable; recovery depends on external backups, not Compose | Host monitoring / service health | Restore drill **not yet established** | High; ops |

## Manual failure drill (isolated environment ONLY)

1. Start a disposable Compose project with dedicated volumes; **do not** run destructive `docker compose down -v` on the existing development stack (which contains payment data).
2. Capture baseline: health, outstanding outbox count, ledger count, Kafka availability, trace IDs.
3. Exercise one failure at a time; verify payment state and outbox/ledger invariants and time to recovery.
4. Restore service; look for duplicate ledger or stuck events; compare against baseline and attach logs with secrets removed.
5. Record pass/fail, incident duration, manual actions, and follow-up ticket for uncovered gaps.

## Triage / rollback principles

- Use `docs/runbooks/outbox-backlog.md` and `docs/runbooks/payment-provider-degradation.md` for existing operational guidance.
- **Never** delete/mark outbox rows as published to hide a backlog; no `flyway repair` or manual schema-history edits to force deployment.
- For a broken application release, redeploy a previously verified commit-SHA image and verify health, event backlog and DB compatibility. Code rollback does **not** automatically roll back DB schema.
- For suspected duplicate/unknown PSP transaction, freeze automated replays until the idempotency and reconciliation contract is confirmed.
