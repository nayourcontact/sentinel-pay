# ADR 0005 — Baseline migration for legacy Flyway consistency

**Status:** Accepted in current repository configuration (documented 2026-10-11)

## Context
`V1__init.sql` and `V2__create_ledger_entries.sql` both create `ledger_entries`, with incompatible definitions. An existing `payments` database had V1, V2 and V3 applied (all successful) and six ledger entries. Rewriting historical migrations would risk Flyway checksum breaks.

## Decision
Keep V1–V3 immutable. Use `B3__schema_after_legacy_migrations.sql` for fresh databases; leave existing versioned history intact; apply new schema changes with V4+ migrations. Tests use the production migration location.

## Alternatives
- Edit V1/V2: rejected due to checksum and history mismatch.
- `CREATE TABLE IF NOT EXISTS` in V2: rejected, hides divergent schemas and changes historical SQL.
- Rebaseline existing DB by hand: rejected due to migration audit risk.

## Consequences / verification
Fresh instances start with B3; existing versioned DBs preserve V1–V3. Test both paths; compare constraints and monetary scale with existing deployment; validate with Flyway and Testcontainers. No automatic `repair`, `clean` or history edit. See `docs/flyway-security-hardening.md`.
