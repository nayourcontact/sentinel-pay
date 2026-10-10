# MCP -> Payment Platform: trace propagation fix

## Confirmed cause

The MCP module defined its own `RestClient.Builder` bean using `RestClient.builder()` in `RestClientConfig.java`. That shadowed the Spring Boot auto-configured builder with ObservationRegistry customizations. `PaymentOperationsClient` already injects a `RestClient.Builder`; no application-code change to this class is required.

**Change:** remove `payment-mcp-server/src/main/java/io/sentinelpay/mcp/config/RestClientConfig.java`, allowing the Spring Boot auto-configured builder to be injected.

## Verify (PowerShell)

Rebuild and restart the affected service:

```powershell
docker compose up -d --build payment-mcp-server
```

Run the existing agent test (may take 1-2 minutes on local Ollama):

```powershell
$body = @{ message = 'Investigate payment 61d3cbdb-409d-40a5-af89-07e520733266. Use live MCP tools and return its current status.' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/api/v1/chat' -Headers @{ 'X-API-Key' = 'change-me-agent' } -ContentType 'application/json' -Body $body
```

Replace the API key if your local `.env` overrides it. In Grafana Explore -> Tempo -> TraceQL, search an interval covering the chat request:

```traceql
{ resource.service.name = "sentinelpay-ai-ops-agent" } &&
{ resource.service.name = "sentinelpay-payment-mcp" } &&
{ resource.service.name = "sentinelpay-payment-platform" }
```

Expected: a single trace with the agent HTTP server span, MCP HTTP server span, an MCP outbound HTTP **client** span, and payment-platform inbound HTTP **server** span, with parent IDs linking them. Confirm `http get /internal/ops/payments/{id}` belongs to the same trace ID.

If only a new MCP outbound HTTP client span appears, but the payment-platform span is in a distinct trace, inspect the active context when MCP tool methods execute (possible asynchronous boundary) and confirm `traceparent` injection on the outgoing GET before changing business code. Do not inject a hard-coded trace ID or propagate headers manually without proving this is needed.

## Operational notes

- `docker compose up -d --build` builds with `-DskipTests` in the Dockerfiles; passing the build does not prove tests passed.
- No API contract, SQL schema, business logic, or secret change was made.
- Rollback: restore `RestClientConfig.java` and rebuild just `payment-mcp-server` (but this also restores the instrumentation defect).
- The existing trace export includes `db.vector.query.content` with user input. Redact or disable this attribute for production use.
