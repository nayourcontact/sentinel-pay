# Ticket 3 — Flyway migration consistency + security hardening

## Confirmed facts

- Historical `V1__init.sql` and `V2__create_ledger_entries.sql` both create `ledger_entries` with incompatible definitions. They are **unchanged** in this patch.
- Existing `payments` database (user-provided query): V1 checksum `1147376094`, V2 `2111126796`, V3 `177394277`; all successful; 6 ledger rows; actual ledger `NUMERIC(19,2)`, UNIQUE(payment_id, entry_type), four indexes.
- Test configuration previously ran `db/test-migration` rather than real production migrations.
- Development defaults include shared API keys, database passwords and Grafana credentials; the default compose publishes MCP/management ports.

## Solution

`B3__schema_after_legacy_migrations.sql` is a Flyway **baseline migration**, not the `flyway baseline` command. For fresh databases Flyway chooses B3 instead of replaying the broken historical V1–V3 sequence. It creates the V1 tables, uses the deployed V2 `ledger_entries` schema and includes V3 outbox trace columns. Databases with already applied V1/V2/V3 ignore B3. Later changes must use V4+. Production and Testcontainers now both use `classpath:db/migration`. **Do not modify historic migration files, do not run `flyway repair`, `clean` or manually alter schema history.**

There is no new migration that changes existing data. Note: historical V1 ledger foreign key and `amount(19,4)` are NOT in the deployed V2 ledger table; B3 intentionally matches the observed deployed V2 schema instead. Review referential-integrity requirements separately before introducing a future V4.

## Security changes

- Internal Ops controller fails closed if the configured shared secret is missing/blank; comparison uses `MessageDigest.isEqual`.
- AI Agent shared API key uses constant-time byte comparison; HTTP Basic authentication is disabled to avoid an alternative login surface.
- `docker-compose.production.yml` overlay requires externally supplied DB/API/Grafana secrets and limits exposed application/monitoring ports to loopback. **Keep the existing compose for local-only development.**
- The overlay is a hardening aid, not a complete production deployment: reverse proxy TLS, authentication on MCP/Actuator/Prometheus, egress rules and secret storage must be designed before public exposure. Local port binding does not prevent other containers on the shared Docker network from connecting.
- Do not paste real secrets into Git. If Docker named volumes were initialized with old passwords, changing `POSTGRES_PASSWORD` does **not** rotate existing PostgreSQL passwords. Rotate users with a separate, controlled DB operation and back up first.
- No dependency vulnerability scan was executable in this environment. Run your organization's dependency/SCA scanner as a separate gate; do not claim dependencies are vulnerability-free.
- OTel payload/trace-attribute inspection was limited to source configuration and requires live telemetry verification.

## Verification (run on a workstation with Maven and Docker)

```powershell
mvn -pl payment-platform,ai-ops-agent -am clean verify
docker compose -f docker-compose.yml -f docker-compose.production.yml config --quiet
```

The FlywayBaselineIT uses a **fresh PostgreSQL Testcontainers** instance, runs production migration locations, checks the version 3 baseline and validates the resulting numeric scale. Existing Flyway database must also be validated **in a backup/staging copy**; do not point destructive test tooling at your live `payments` volume. Verify V1/V2/V3 checksums against actual source files before rollout.

### Expected results

- Tests succeed including `FlywayBaselineIT`, `OpsQueryControllerTest`, `ApiKeyAuthenticationFilterTest`.
- A new DB contains B3 baseline history, actual `ledger_entries.amount` scale 2, and trace columns.
- Existing DB stays at its original V1/V2/V3 rows and 6 ledger entries; Flyway validation succeeds with B3 added.
- Missing production secrets cause compose configuration to fail instead of falling back to dev values.

## Rollout / rollback

1. Back up the existing payments database.
2. Verify Flyway historical checksums match the checked-in V1/V2/V3. If not, stop and investigate before deployment.
3. Run clean-DB Testcontainers suite and existing-DB validation in staging.
4. Supply strong credentials from external env/secret store, ensure the DB credentials are actually rotated, and deploy with the production overlay only behind an authenticated proxy.
5. Rollback code/config to prior deployment if required; B3 adds no DDL to already migrated production DBs. A fresh DB initialized by B3 can continue using future V4+ migrations.

**Status:** patch prepared; tests and production compose validation are not claimed as executed in this sandbox (Maven/Docker unavailable).
