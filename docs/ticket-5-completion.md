# Ticket 5 completion checklist

Scope: **finish monitoring / SLI-SLO, API versioning, DB migration strategy, System Design case study**.

| Deliverable | State | Reference |
|---|---|---|
| Monitoring topology and check procedure | DOCUMENTED | `docs/monitoring-sli-slo.md`, existing infra configs |
| Proposed measurable SLIs / SLOs | PROPOSED, NOT MEASURED | `docs/monitoring-sli-slo.md` |
| API version inventory and compatibility policy | DOCUMENTED; no new route | `docs/api-versioning-policy.md` |
| DB migration strategy | ALREADY IMPLEMENTED/DOCUMENTED in Ticket 3 | `docs/flyway-security-hardening.md`, `docs/adr/0005-baseline-migration-legacy-compatibility.md` |
| System Design case study | DOCUMENTED | `docs/system-design-case-study.md` |
| Read-only local monitoring smoke checker | ADDED; requires user runtime execution | `scripts/verify-observability.ps1` |

## Required evidence before ticket sign-off

- [ ] Execute `scripts/verify-observability.ps1` on local Docker host; capture output.
- [ ] Inspect Tempo trace + Grafana datasources and record result (manual).
- [ ] Confirm GitHub Actions CI checks on documentation branch / PR.
- [ ] Label proposed SLO thresholds as proposals until measured and approved.

No production SLO claim, load test or API URL migration is included. Those are separate implementation decisions. Commit as `docs(operations): finalize SLI SLO API policy and system design case study`.
