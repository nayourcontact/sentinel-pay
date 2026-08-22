# ADR 0001: Start as a modular runtime, not a microservice zoo

## Status
Accepted

## Context
A portfolio payment system needs realistic service boundaries without creating operational complexity that adds no business value. Splitting every noun into a service would make local execution and transactional reasoning worse.

## Decision
Keep payment command handling, orchestration and projections in one Spring Boot deployable while enforcing package-level hexagonal boundaries and communicating across important workflow boundaries using Kafka.

## Consequences
- Local development remains one-command reproducible.
- Domain/application code does not depend on Spring MVC or JPA.
- Async boundaries can later become separately deployed processes.
- This does not claim independent scaling for every module today.
