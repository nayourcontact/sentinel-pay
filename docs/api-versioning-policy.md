# Ticket 5 — API versioning and compatibility policy

## Actual API surface (2026-10-11 snapshot)

- `PaymentController`: `POST /api/payments` and `GET /api/payments/{id}`; `POST` requires `Idempotency-Key`. A successful create returns `201 Created` and a `Location` under the same unversioned path.
- `OpsQueryController`: read-only internal `/internal/ops/...` queries protected with internal API key; intended for the MCP server, **not** public API.
- `payment-mcp-server`: its MCP server declares version `1.0.0`; this is a **protocol server version**, not a payment REST API version.

### Decision: document existing v1 contract; defer URL migration

The current REST API is **unversioned**; call it the **legacy v1 contract** in documentation. Do NOT rename endpoints or assert `/api/v1/payments` exists. A forced path rewrite would break UI, clients, `Location` headers and current tests. Maintain exact paths for now.

When a breaking change becomes necessary, introduce new routes *in parallel* (`/api/v2/...`) with dedicated DTOs, tests and client migration window. Keep existing `/api/payments` contract until explicitly retired. No version routing is implemented by this documentation ticket.

## Compatibility rules

- Additive optional response fields: generally backward-compatible for tolerant clients, but contract tests must verify clients.
- Removing/renaming fields, changing types/semantics/status codes, changing idempotency scope, or changing required request properties: **breaking**, require a new version and an ADR.
- Introduce request fields as optional with safe defaults first; never repurpose existing fields or enum meanings silently.
- Keep `Idempotency-Key`, `201 Created`, response `Location`, error status conventions and payment amount precision unchanged for legacy endpoints.
- Internal `/internal/ops` API uses coordinated deployment and negative auth tests; do not treat it as publicly stable. MCP tool schema changes require agent/tool compatibility review.
- Database migrations remain separate from HTTP API versions. Use expand/contract approach and backward-compatible deployment windows; historical Flyway V1–V3 are immutable, B3 bootstraps empty DBs, future migrations V4+.

## Release checklist

1. Enumerate changed route, request/response, status and idempotency behavior.
2. Compare to current `PaymentController`, Ops controller and MCP tool consumers.
3. Keep legacy contract intact or prepare parallel version and explicit client migration.
4. Add MockMvc/contract tests for existing contract and intended new one.
5. Document deprecation communication and sunset criteria; do not invent deadlines.
6. Validate migration compatibility and rollback before deployment.

**Status:** policy and verified inventory complete; `/api/v1` or `/api/v2` implementation is deliberately **out of scope**.
