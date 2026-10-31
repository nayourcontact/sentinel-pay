# SentinelPay OpenAPI / Swagger UI

## Scope

- `payment-platform`: `/api/payments/**`, `/internal/ops/**`
- `ai-ops-agent`: `/api/v1/**`
- `payment-mcp-server`: MCP protocol, **not** a conventional REST API; excluded from Swagger.

Spring Boot 4.1.0 uses `springdoc-openapi-starter-webmvc-ui:3.2.0`.

## Defaults / security

- `OPENAPI_ENABLED=false` by default. Enable **only** in an isolated local or internal environment.
- API JSON: `/v3/api-docs`; UI: `/swagger-ui.html`.
- In JWT mode, both endpoints and Swagger assets require `ADMIN` role. `AGENT_USER` and `OPS_READER` cannot read the documentation.
- The legacy Payment Platform security mode already permits all requests; **never enable Swagger on an externally exposed instance in that mode**.
- The legacy Agent security chain still requires its API key; it is not relaxed for Swagger.
- Bearer JWT is described for the REST APIs. `/internal/ops/**` additionally requires `X-Internal-Ops-Key`; do not put secrets in examples or repositories.
- Do not expose these endpoints publicly in production. Disable by default; restrict with authentication, ingress policy and TLS if explicitly enabled.

## Validation (PowerShell 5.1)

```powershell
mvn -pl payment-platform,ai-ops-agent -am test
mvn clean verify
```

Use existing isolated instances; enable `OPENAPI_ENABLED=true` **only** there and rebuild. With JWT enabled:

```powershell
curl.exe -sS -o NUL -w "HTTP %{http_code}`n" http://127.0.0.1:18085/v3/api-docs
curl.exe -sS -o NUL -w "HTTP %{http_code}`n" http://127.0.0.1:18083/v3/api-docs
```

Expected without token: 401; with valid non-ADMIN token: 403. An ADMIN token should receive 200 and OpenAPI JSON with expected paths. When OPENAPI_ENABLED=false, the documentation must not be exposed (in JWT mode the security layer may deny it before returning 404).

For Swagger UI, confirm `/swagger-ui.html` and its assets are protected the same way. Test a real authenticated browser session as applicable; the Swagger UI is not an OAuth login flow by itself. Do not weaken existing JWT/RBAC controls for documentation.

## Rollback

Set `OPENAPI_ENABLED=false` and redeploy; remove the springdoc dependency/configuration only if the new library causes an incompatibility.
