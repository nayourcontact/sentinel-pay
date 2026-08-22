# Runbook: Transactional Outbox Backlog

A growing pending outbox count may indicate Kafka unavailability, publisher failure, or sustained downstream pressure. Failed events have exhausted retry policy and require operator investigation. Never mark a failed event as published merely to clear a dashboard.
