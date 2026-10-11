# Ticket 4 — Architecture diagram, ADRs, NFR, failure scenarios

## Delivered

- [x] **Architecture views:** `docs/architecture-overview.md` (Mermaid container topology, write sequence, AI trust boundary).
- [x] **Existing ADRs reviewed and retained:** `docs/adr/0001-*` through `0004-*`; additional AI/MCP/RAG boundary ADR remains unchanged.
- [x] **Decisions added:** ADR 0005 (Flyway legacy migration), 0006 (Compose CI/CD), 0007 (MCP resilience).
- [x] **NFRs:** `docs/non-functional-requirements.md` (12 traceable requirements; proposed vs observed; test methods).
- [x] **Failure scenarios:** `docs/failure-scenarios.md` (13 cases, detection, validation/recovery, risks).
- [x] **No runtime or schema change:** documentation-only patch. Existing API contracts remain untouched.
- [ ] **Optional future validation:** render Mermaid in GitHub; execute fault drills in disposable Compose stack; establish business SLO/RPO/RTO targets.

## Acceptance and limits

The documentation describes **real repository structure** and explicitly separates established behavior from desired outcomes. No availability load test, HA drill or production backup restore was performed by this documentation change. Do not claim these capabilities as proven.

## Verification

```powershell
git diff --check
git status --short
# Optionally preview Mermaid code fences on GitHub / VS Code Markdown preview.
```

**Recommended commit:** `docs(architecture): add diagrams ADRs NFRs and failure scenarios`
