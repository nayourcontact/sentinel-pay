# Infrastructure as Code — SentinelPay (Ticket 7)

## Scope and cost

This ticket formalizes the existing **local Docker Compose** infrastructure as code. It provisions no cloud infrastructure and requires no AWS account or billable cloud service. Docker Desktop licensing can depend on organization size/use; verify your license terms. Images, local disk, CPU, memory and power remain local resource costs.

Terraform/AWS creation is **out of scope**; Terraform CLI may be free but AWS resources generally are not.

## Versioned infrastructure

- `docker-compose.yml`: development services and persistence.
- `docker-compose.performance.yml`: separate Compose project, database, Kafka and payment platform on loopback port 18085; **destructive write tests only here**.
- `docker-compose.production.yml`: production-oriented overrides; **not** a production-hardening certification.
- `docker-compose.release.yml`: references immutable CI-published application images via commit SHA.
- `infra/prometheus/`, `infra/grafana/`, `infra/tempo/`: monitoring configuration.
- `.env.example`: sample variable names; never commit `.env`, credentials or Docker volumes.

## Read-only IaC validation

Run from the repository root on Windows with Docker Desktop available:

```powershell
.\scripts\verify-infrastructure.ps1
```

Equivalent manual checks:

```powershell
docker compose -f docker-compose.yml config --quiet
docker compose -p sentinelpay-perf -f docker-compose.performance.yml config --quiet
```

These checks **do not** start or remove containers. `config` can resolve environment variables; avoid logging full resolved configuration because it may contain secrets.

## Lifecycle (manual, opt-in)

```powershell
# Existing development stack; uses existing local Docker resources
docker compose -f docker-compose.yml up -d --build
docker compose -f docker-compose.yml ps

# Dedicated isolated performance stack
docker compose -p sentinelpay-perf -f docker-compose.performance.yml up -d --build
docker compose -p sentinelpay-perf -f docker-compose.performance.yml ps

# Stop only performance stack, keep its volume
docker compose -p sentinelpay-perf -f docker-compose.performance.yml down
```

Never run `down -v` against a data-containing environment without backup/approval. Keep production and development service secrets separate. Published ports and Docker network boundaries must be reviewed for deployment target. Validate stateful migrations/backup separately.

## Acceptance criteria

- [ ] Both Compose files pass `config --quiet` locally.
- [ ] Existing development services remain unaffected by validation.
- [ ] Performance project uses its own named Docker volume and isolated database/Kafka service names.
- [ ] No Terraform apply, AWS provisioning or paid cloud resources.
- [ ] Documentation includes start, inspect, stop and data-safety instructions.

## Limitations

This is local-container IaC, **not** infrastructure provisioning for AWS or Kubernetes. The validation is syntax/static configuration only, not a deployment, performance, security or disaster-recovery audit.
