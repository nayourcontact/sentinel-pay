# Ticket 8 — Local Keycloak JWT/RBAC integration (no cloud charges)

## Scope and safety

Use **only** the separate `sentinelpay-perf` PostgreSQL/Kafka/Payment Platform stack for JWT tests. The default SentinelPay deployment remains untouched. Never combine the default `docker-compose.yml` with the JWT override. Local Keycloak uses an embedded development database, and is **not production hardened**. Its HTTP listener is bound to `127.0.0.1:18088`. No cloud services or external accounts are used. The files under `.local/` contain credentials, are ignored by Git, and should never be uploaded.

The new JwtSupportConfiguration files replace the Ticket 8 versions in **both** modules. When `APP_SECURITY_JWT_ENABLED=true`, they also require `APP_SECURITY_JWT_AUDIENCE` and validate the signed JWT's `aud` claim. The configuration allows `http://host.docker.internal:` as a JWKS development address; do **not** expose this permissive local option in production. Importantly issuer validation still requires `http://localhost:18088/realms/sentinelpay`, independently of the JWKS retrieval URL.

## 1. Prepare

From the repository root, after extracting this patch:

```powershell
# Append /.local/ to .gitignore once, then:
.\scripts\init-keycloak-local.ps1
docker compose -p sentinelpay-auth -f docker-compose.keycloak.yml up -d
curl.exe -i http://127.0.0.1:18088/realms/sentinelpay/.well-known/openid-configuration
```

The initializer generates random passwords for `reader`, `operator`, `agent-user`, `ops-reader`, `no-role` and an admin account under `.local/keycloak/`. It refuses to overwrite existing credentials. Realm import happens on Keycloak **first startup**. If you imported an older realm already, use a fresh disposable Keycloak container and database; do not delete shared volumes.

## 2. Start JWT-secured isolated Payment API

Keep local Keycloak running; update the two Java decoder files; then:

```powershell
mvn -pl payment-platform,ai-ops-agent -am clean verify

docker compose -p sentinelpay-perf -f docker-compose.performance.yml -f docker-compose.jwt-test.yml up -d --build --force-recreate perf-payment-platform
curl.exe -i http://127.0.0.1:18085/actuator/health
```

The performance stack already has isolated data and Kafka, and the normal service on `8085` stays untouched. For key discovery from the payment container, `host.docker.internal:18088` must be reachable; on Docker Desktop it normally is. If a 401 results for every valid token, check JWKS access and JWT decoder startup logs rather than disabling issuer or audience validation.

## 3. Test token issuance and RBAC

Find an **existing isolated performance payment** ID (not your retained normal DB):

```powershell
docker compose -p sentinelpay-perf -f docker-compose.performance.yml exec perf-db psql -U payments -d payments -c 'SELECT id FROM payments ORDER BY created_at DESC LIMIT 1;'
.\scripts\verify-jwt-local.ps1 -PaymentId 'PUT_EXISTING_ISOLATED_PAYMENT_UUID_HERE'
```

Expected `401` for no/invalid bearer; `403` for a valid wrong-role token; `200` for GET with `PAYMENT_READER` or `PAYMENT_OPERATOR`; `403` for reader POST. An actual operator POST would insert a payment into the disposable test DB and is intentionally not performed by this smoke script. Test invalid issuer/audience/expiry with focused Spring Security tests as a separate quality gate; mere status smoke tests do not cover everything.

Tokens are acquired by the test script using a public development client with direct password grant; **do not reuse this grant or credentials in production**. Replace with Authorization Code + PKCE or service client credentials where applicable.

## 4. Known limitations and follow-up

- Payment MCP Server currently uses `X-Internal-Ops-Key`, not bearer propagation. JWT enforcement in the normal Payment Platform would break MCP ops calls. Keep strict mode on the isolated stack until client credential propagation is implemented.
- AI Ops Agent JWT/RBAC role behavior and legacy API-key compatibility still require focused tests. These scripts test Payment API only.
- The role mapper emits Keycloak realm roles into the signed top-level `roles` claim and an audience mapper emits `sentinelpay-api` into `aud`.
- Local Keycloak is a demonstration IdP; no HA, TLS, persistence backups, refresh token policy hardening or identity governance.
- After changing Keycloak configurations, confirm token claims before enabling any production endpoint.

## 5. Cleanup and rollback

```powershell
# Stop JWT test workload without touching its isolated DB volume:
docker compose -p sentinelpay-perf -f docker-compose.performance.yml -f docker-compose.jwt-test.yml down
# Stop the Keycloak dev container:
docker compose -p sentinelpay-auth -f docker-compose.keycloak.yml down
```

Do not use `down -v` on any shared project. To revert the JWT code change, revert the specific commit, rebuild the isolated service and rerun Maven verification. Never disable JWT as a workaround on an externally accessible payment API.
