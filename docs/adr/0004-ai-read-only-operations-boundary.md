# ADR 0004: AI Operations Tooling Is Read-Only by Default

## Decision
The AI agent reaches live payment state only through the Payment MCP Server. The initial MCP surface contains read-only, idempotent diagnostic tools.

## Rationale
An LLM is probabilistic and can be influenced by malicious or misleading input. Direct SQL, shell, Kafka administration or unrestricted internal APIs would create an unacceptable blast radius.

## Consequences
- Diagnostics can be automated safely.
- Remediation is recommended but not executed by the current agent.
- A future write tool must require a separately authenticated command path, persisted approval, audit record, deterministic validation and idempotency key.
