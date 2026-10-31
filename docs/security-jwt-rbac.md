# Ticket 8 — Spring Security OAuth2 Resource Server / JWT / RBAC

## Scope and compatibility

This patch adds **opt-in JWT verification** to `payment-platform` and `ai-ops-agent`. It deliberately does **not** implement a token issuer or authorize the MCP server itself. Enable only when a trusted external/local OIDC provider issues RS256/compatible signed JWTs and exposes a JWKS endpoint. No AWS services are required.

Default `APP_SECURITY_JWT_ENABLED=false` preserves existing HTTP/API-key contracts during migration. **Do not describe that default as production-secure.** When enabled, all protected API endpoints require a validated Bearer token, subject to the agent's existing X-API-Key filter for `AGENT_USER` only. The payment `/api/ops/**` endpoints additionally retain their original `X-Internal-Ops-Key` verification. The MCP service will need a matching Bearer token forwarding strategy before strict mode is enabled on the payment service; otherwise operations calls will fail.

JWT requirements: issuer (`iss`) matches configured `APP_SECURITY_JWT_ISSUER_URI`; token must be signed by a key in `APP_SECURITY_JWT_JWK_SET_URI`; default Spring validators check expiration and not-before. Roles are read **only** from a signed top-level `roles` array (e.g. `"roles":["PAYMENT_READER"]`), not from untrusted request headers. Configure the identity provider's role mapper to issue that claim. Audiences and trusted issuer transport should be added and independently verified before any production exposure. Use HTTPS for remote JWKS and protect secrets.

## RBAC contract

| Module | Route | Accepted roles |
| --- | --- | --- |
| Payment | GET `/api/payments/{id}` | `PAYMENT_READER`, `PAYMENT_OPERATOR`, `ADMIN` |
| Payment | POST `/api/payments` | `PAYMENT_OPERATOR`, `ADMIN` |
| Payment | `/api/ops/**` | `OPS_READER`, `ADMIN` **and existing internal key** |
| Agent | POST `/api/v1/chat` | `AGENT_USER`, `ADMIN` (API key supported) |
| Agent | GET `/api/v1/knowledge/search` | `AGENT_USER`, `KNOWLEDGE_WRITER`, `ADMIN` |
| Agent | POST `/api/v1/knowledge`, `/api/v1/knowledge/files` | `KNOWLEDGE_WRITER`, `ADMIN` |
| Both | actuator health/info | public |
| Both | actuator prometheus/metrics | `OPS_READER` |

## Startup (opt-in)

Set on **each service independently**:

```powershell
$env:APP_SECURITY_JWT_ENABLED = 'true'
$env:APP_SECURITY_JWT_ISSUER_URI = 'http://localhost:8088/realms/sentinelpay'
$env:APP_SECURITY_JWT_JWK_SET_URI = 'http://localhost:8088/realms/sentinelpay/protocol/openid-connect/certs'
```

The values above are an **example only**: no Keycloak realm/container is provisioned by this patch. Docker service-to-service networking may require different reachable JWKS URL and matching issuer; don't use an unreachable localhost inside a container. Importantly, issuer mismatch must fail authentication rather than be bypassed.

Never commit JWTs, private signing keys or actual credentials. No changes to Flyway migrations.

## Validation gates

1. `mvn -pl payment-platform,ai-ops-agent -am clean verify` — compile and run tests (PostgreSQL Testcontainers requires Docker).
2. With JWT mode disabled, rerun existing local integrations and performance tests.
3. With JWT mode enabled and a real test IdP: missing/invalid/expired JWT = 401; valid signed JWT with insufficient `roles` = 403; correct role = success.
4. Test ops endpoint with both valid JWT and existing internal key; verify without either fails.
5. Test agent X-API-Key legacy integration with new RBAC; test `KNOWLEDGE_WRITER` restrictions.
6. Before any strict-mode production deployment add `aud` validation for each service, stable Keycloak/IdP provisioning, and bearer propagation from MCP or service credential flow. Keep management metrics on a private network.

## Rollback

Set `APP_SECURITY_JWT_ENABLED=false` for local regression/rollback; this removes JWT authorization checks, **so do not treat rollback as safe in an exposed production environment**. Roll back the application version after validating dependent clients. Review deployment controls and access exposure before rollout.
