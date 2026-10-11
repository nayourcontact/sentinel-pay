# SentinelPay Ticket 6 — isolated write/race benchmark

This is an **additional** compose project; never combine its YAML with the normal `docker-compose.yml`.
It creates a separate PostgreSQL volume, Kafka broker, Docker internal network, and application container. It has no Compose service dependencies on the existing stack.

## Run from Windows PowerShell (repository root)

```powershell
docker compose -p sentinelpay-perf -f docker-compose.performance.yml up -d --build
docker compose -p sentinelpay-perf -f docker-compose.performance.yml ps
curl.exe -i http://localhost:18085/actuator/health
.\scripts\run-performance-docker.ps1 -Mode write
.\scripts\run-performance-docker.ps1 -Mode race
```

Write test: 1 RPS for 30 seconds. Race test: 5 simultaneous POST requests with the same idempotency key. Record results in `docs/performance-test-report.md`. Failed race checks are **real findings**, not automatically acceptable.

## Check isolation and data

```powershell
docker compose -p sentinelpay-perf -f docker-compose.performance.yml exec perf-db psql -U payments -d payments -c "SELECT COUNT(*) FROM payments;"
```

## Cleanup — isolated stack ONLY

```powershell
docker compose -p sentinelpay-perf -f docker-compose.performance.yml down
```

**Optional destructive cleanup, deletes only this project's performance DB volume:**

```powershell
docker compose -p sentinelpay-perf -f docker-compose.performance.yml down -v
```

Do not run `docker compose down -v` against the default SentinelPay stack.

## Notes

- `127.0.0.1:18085` is host-only; the internal Compose network blocks outbound Internet traffic. Kafka may still require Docker image access during initial pull/build.
- `grafana/k6:latest` is convenient for local use; pin a specific version/digest for reproducible CI.
- The k6 scripts presently import `randomUUID` from jslib.k6.io; the k6 runner needs outbound access when resolving this module.
- This is an isolated integration load-test environment, not a representative production performance environment.
- If startup fails, inspect `docker compose -p sentinelpay-perf -f docker-compose.performance.yml logs --tail=100 perf-payment-platform` and `perf-kafka`.
