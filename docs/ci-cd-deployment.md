# Ticket 2 — Deployment + CI/CD

## Scope and decision

**CI:** GitHub Actions runs Java 21 `mvn verify` (Surefire and Failsafe integration tests) and a reproducible Node 22 `npm ci && npm run build` on pushes/PRs to `main`.

**CD (release publishing):** After **both** CI jobs pass on `main`, GitHub Actions builds four existing Dockerfiles and publishes immutable images to GitHub Container Registry (GHCR), tagged `sha-<full git commit SHA>`.

**Deployment:** A deliberate **manual** Docker Compose pull/up on a target machine. No cloud account, SSH access, server, automatic production release, or deployment credentials are assumed. The target environment must be selected separately. This is a container-release pipeline plus a documented deployment procedure, **not proof of live production deployment**.

## Prerequisites

- GitHub repository's Actions workflow permissions permit `GITHUB_TOKEN` to **write packages** for GHCR publishing, and any applicable organizational package restrictions allow creation.
- The target runs a recent Docker Compose v2 and can access the published GHCR packages (use `docker login ghcr.io` with a **read:packages** token for private packages; do not paste tokens into commands/history).
- A private `.env`/external secret solution is configured. The current base Compose file includes **development defaults** for database and service credentials and publishes ports; **do not expose it as production-ready on the internet**. Harden authentication, TLS, networking, image provenance, database backups and secrets before production.
- A successful `main` workflow run contains all four published images for the requested SHA.
- Docker builds require network access to resolve the Maven and npm dependencies and pull base images.

## CI checks

- Java job: `mvn -B --no-transfer-progress verify`. Integration tests rely on Testcontainers/Docker; reports are uploaded as an artifact if Java fails.
- UI job: `npm ci` then `npm run build`. Using lockfile-based install means the lockfile must be up to date.
- Publish job: only on `main` push/manual workflow dispatch **after** both jobs pass; never on pull requests. Built artifacts are four per-service GHCR images, pinned by SHA.
- Image builds package application binaries with `-DskipTests`; test verification is the mandatory earlier job, not a second stage hidden in Dockerfiles.

## Manual deployment (development / private demo host)

Get the **exact git SHA** from the successful workflow and use the same repository contents on the target machine. From the repository root:

```bash
export GHCR_REPOSITORY=nayourcontact/sentinel-pay
export RELEASE_SHA=<full-40-character-sha>
# Set secrets out-of-band, e.g. in a private .env; never commit them.
docker compose -f docker-compose.yml -f docker-compose.release.yml config --images
docker compose -f docker-compose.yml -f docker-compose.release.yml pull payment-platform payment-mcp-server ai-ops-agent ops-ui
docker compose -f docker-compose.yml -f docker-compose.release.yml up -d --no-build
# Verify health and functionality:
docker compose -f docker-compose.yml -f docker-compose.release.yml ps
```

The release overlay replaces the four app images, while base Compose retains PostgreSQL, Kafka, Ollama, Tempo, Prometheus and Grafana. `--no-build` ensures application images are used instead of locally rebuilt. `docker compose config --images` verifies image tags before starting.

**Important:** `docker compose up` also starts databases and infrastructure, and version updates may have migrations or state implications. Keep backups and verify the schema before applying to environments holding data. The base deployment is currently a development/demo topology.

## Post-deploy verification

- `docker compose ... ps`: services healthy; failed healthchecks/logs investigated.
- API responds on the expected configured route, MCP tool can perform a read-only operation, UI loads, and metrics are exposed only as authorized.
- Check container logs and Prometheus/Grafana for exceptions, request latency, outbox/Kafka consumer backlogs and provider failures.
- Do not mark deployment successful from `docker compose up` exit status alone.

## Rollback

Use the last known-good **successful CI** SHA and repeat `pull`/`up -d --no-build` with `RELEASE_SHA` set to that SHA. Reverting images does **not** roll back database migrations or lost data; migration compatibility/backup validation is required first.

## Failure diagnosis

The screenshot from 2026-10-11 shows an **earlier** GitHub Actions `java` job failure for OpenTelemetry commit `4acea9a`, while UI succeeded; the screenshot does **not** show the failed Maven command or stack trace. Do not infer root cause from `Process completed with exit code 1`. Open Actions → failing run → `java` → `Build and verify Java modules`, or download `java-test-reports` after this workflow is merged. Address the specific compiler/test/Testcontainers failure rather than disabling tests. A later test run appears successful in the supplied screenshot but that is a distinct commit.

## Acceptance checklist

- [x] Existing CI checked; Java and UI gates maintained
- [x] Repeatable UI build with `npm ci`
- [x] Publish four immutable SHA-tagged Docker images after passing CI
- [x] Manual Compose release overlay and documented rollback
- [x] No production credentials or remote-deployment assumptions embedded in workflow
- [ ] GitHub Actions Java and UI jobs confirmed green on new commit
- [ ] GHCR packages published and pull permissions verified
- [ ] Manual deployment/healthcheck executed on chosen target (if required for ticket acceptance)

## Out of scope

Automated production deployment, Kubernetes, Terraform, cloud provisioning, production secrets/TLS hardening, blue-green deployment, and automatic database rollback.
