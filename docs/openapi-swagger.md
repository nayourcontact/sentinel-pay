# SentinelPay OpenAPI / Swagger UI

## Scope

- `payment-platform`: `/api/payments/**`, `/internal/ops/**`
- `ai-ops-agent`: `/api/v1/**`
- `payment-mcp-server`: MCP protocol, **not** a conventional REST API; excluded from Swagger.

Spring Boot 4.1.0 uses `springdoc-openapi-starter-webmvc-ui:3.2.0`.

## Defaults / security

- `OPENAPI_ENABLED=false` by default. Enable **only** in an isolated local or internal environment.
- API JSON: `/v3/api-docs`; UI: `/swagger-ui.html`.
- **Payment Platform:** when `OPENAPI_ENABLED=true`, Swagger UI and OpenAPI JSON are accessible without login (HTTP 200 in the isolated test). This exposes API metadata, not permission to call protected payment endpoints. Do not enable documentation on public production deployments.
- **AI Ops Agent:** in JWT mode, the documentation remains restricted to `ADMIN`; its runtime behavior needs a separate smoke test.
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

Expected for an isolated Payment Platform with OpenAPI enabled: **200 without a token** on `/v3/api-docs` and `/swagger-ui/index.html`. Payment business endpoints remain protected by JWT/RBAC. The AI Ops Agent in JWT mode should return 401 without a token, 403 for valid non-ADMIN roles and 200 for ADMIN. When `OPENAPI_ENABLED=false`, the documentation must not be exposed (the security layer may deny requests before returning 404).

For Swagger UI, test `/swagger-ui.html`, `/swagger-ui/index.html` and API JSON separately. The Payment Platform documentation is intentionally public **only when enabled**; this behavior must be understood before exposing the instance. The Swagger UI itself is not an OAuth login flow.

## Rollback

Set `OPENAPI_ENABLED=false` and redeploy; remove the springdoc dependency/configuration only if the new library causes an incompatibility.
