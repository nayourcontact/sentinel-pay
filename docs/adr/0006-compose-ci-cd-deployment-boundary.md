# ADR 0006 — GitHub Actions, GHCR and manual Compose deployment

**Status:** Accepted as current delivery approach (documented 2026-10-11)

## Context
The repository includes three Spring Boot services, a React UI, Dockerfiles, a shared Compose topology and GitHub Actions verification. A full orchestration platform would add unnecessary cost and configuration to the present demo scope.

## Decision
Run Maven `verify` and UI build on CI; on successful main-branch CI publish four images to GHCR using commit-SHA tags. Deploy with a reviewed manual Docker Compose flow and keep a known-good SHA for code rollback.

## Alternatives
- Kubernetes/CD controller: deferred; no evidence of HA or autoscaling requirements.
- Unversioned `latest` image only: rejected due to ambiguous rollback provenance.

## Consequences / verification
Release provenance is traceable. Compose remains single-host and does not guarantee zero downtime, HA, authentication, backup or automatic rollout. `docs/ci-cd-deployment.md` describes operation. Confirm each release in Actions and verify actual deployed image SHA before declaring deployment complete.
