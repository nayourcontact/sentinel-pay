# Threat model

## Primary threats

1. Prompt injection in retrieved documents or tool output.
2. Excessively powerful MCP tools.
3. Data leakage through prompts, logs or tool responses.
4. Unbounded retrieval or tool execution causing cost / latency spikes.
5. Cross-tenant access when the architecture is extended to multi-tenancy.

## Baseline controls included

- Read-only, narrowly scoped MCP tools.
- DTO outputs rather than persistence entities.
- Structured server-side validation.
- RAG and MCP data explicitly treated as untrusted input in the system prompt.
- API-key boundary for the demo HTTP API.
- Request size validation.
- Result limits on payment search and vector search.
- Health, metrics and Prometheus endpoints.
- Secrets configured through environment variables.

## Production upgrades

Replace demo API-key auth with OIDC/OAuth2 Resource Server, add tenant-aware authorization, policy enforcement, immutable audit storage, network segmentation, TLS/mTLS, secret rotation, rate limiting and human approval for side-effecting tools.
