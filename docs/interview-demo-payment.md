# 10-minute interview demo

1. Open the architecture diagram in the README and explain why this is a modular runtime rather than seven fake microservices.
2. Create a payment in the operations console.
3. Repeat the same payment with the same idempotency key using curl and show that no duplicate aggregate is created.
4. Show `Payment.requestAuthorization()` and `markAuthorized()` to demonstrate domain-owned state transitions.
5. Show the `PaymentApplicationService` transaction: payment and outbox intent are persisted atomically.
6. Show the `SKIP LOCKED` outbox query and explain multi-instance polling.
7. Show the authorization consumer and explain at-least-once delivery.
8. Show ledger uniqueness and webhook event identity as examples of consumer idempotency.
9. Open Prometheus/Grafana and mention SLOs you would define for authorization latency and error rate.
10. Finish with ADR 0003: exactly-once is not claimed across database + Kafka + external PSP; business idempotency is the actual guarantee.
