# SentinelPay - minimal architecture testing checklist

The existing project already contains substantial unit coverage and six PostgreSQL/Kafka Testcontainers classes. The integration tests use the `*IT.java` suffix; they run in Maven **verify**, not `mvn test`. This patch aligns three source filenames with their Java class names so Maven test compilation can succeed.

## Run

Requirements: Java 21, Maven, Docker Desktop with Linux containers and Docker Engine running.

```powershell
.\RUN_TESTS.ps1
```

Or:

```powershell
mvn -pl payment-platform -am verify
```

## Evidence by category

| Checklist item | Concrete tests |
| --- | --- |
| Unit tests | `MoneyTest`, `PaymentTest`, `AuthorizationProcessorTest`, `PaymentApplicationServiceTest` |
| Integration tests | `PaymentRepositoryIT`, `OutboxRepositoryIT`, `KafkaAuthorizationIT` |
| Testcontainers | PostgreSQL 17 and Apache Kafka containers in `*IT` tests |
| Kafka integration tests | `KafkaAuthorizationIT.authorizationRequestedEventShouldBeConsumed` |
| Repository tests | `PaymentRepositoryIT`, `OutboxRepositoryIT`, `JpaPaymentRepositoryTest` |
| Idempotency tests | `PaymentIdempotencyIT`, `PaymentIdempotencyPersistenceIT`, plus application service unit tests |

## Notes

- Tests require a functioning Docker daemon. Testcontainers provisions temporary databases and brokers and will pull images the first time.
- Some integration tests start a Spring context and run Flyway. **Do not** point their `spring.datasource.*` settings at a real database.
- A green `mvn test` output only certifies unit tests, not the Testcontainers suite.
- `mvn verify` must finish with `BUILD SUCCESS` and the Failsafe test summaries must show zero failures, errors and unexpected skips.
- This checklist is not proof of concurrency-safe idempotency under simultaneous identical requests, nor does it claim full end-to-end production coverage.
