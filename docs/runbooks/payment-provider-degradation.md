# Runbook: Payment Provider Degradation

## Trigger
Investigate when payment failure rate or authorization latency rises materially above baseline.

## Procedure
1. Get a live incident snapshot through the payment operations MCP tools.
2. Sample failed payments and inspect individual timelines.
3. Check transactional outbox health to rule out internal event publication failure.
4. Compare provider/business declines with infrastructure failures.
5. Recommend traffic shift only when evidence supports provider-specific degradation.
6. Any traffic shift requires human approval through a privileged write path.

## Safety invariant
The AI agent cannot directly mutate payment state or routing using its read-only MCP surface.
