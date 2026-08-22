# ADR 0001: Separate agent orchestration, MCP capabilities and RAG knowledge

## Status
Accepted

## Context
The LLM needs both authoritative live business data and unstructured enterprise knowledge.

## Decision
- Live business operations are exposed through MCP tools.
- Unstructured documentation is retrieved through PGVector RAG.
- The agent application orchestrates both sources.
- The model never connects directly to SQL or infrastructure credentials.

## Consequences
This creates explicit security and ownership boundaries, improves auditability and lets MCP servers evolve independently from model providers.
