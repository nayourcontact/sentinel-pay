package io.sentinelpay.mcp;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class PaymentMcpTools {
    private static final Logger log = LoggerFactory.getLogger(PaymentMcpTools.class);
    private final PaymentOperationsClient payments;
    private final MeterRegistry metrics;

    public PaymentMcpTools(PaymentOperationsClient payments, MeterRegistry metrics) {
        this.payments = payments;
        this.metrics = metrics;
    }

    @McpTool(name = "get_payment", title = "Get payment", description = "Returns authoritative live payment state by UUID. Read-only and idempotent.", generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public Map<String, Object> getPayment(@McpToolParam(description = "Payment UUID", required = true) String paymentId) {
        return audited("get_payment", () -> payments.payment(paymentId));
    }

    @McpTool(name = "get_payment_timeline", title = "Get payment timeline", description = "Returns payment state, outbox lifecycle and ledger projection for one payment. Use for root-cause analysis.", generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public Map<String, Object> getPaymentTimeline(@McpToolParam(description = "Payment UUID", required = true) String paymentId) {
        return audited("get_payment_timeline", () -> payments.timeline(paymentId));
    }

    @McpTool(name = "get_failed_payments", title = "Get failed payments", description = "Returns recent failed payments from the authoritative payment service. Maximum 100.", generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public List<Map<String, Object>> getFailedPayments(@McpToolParam(description = "Maximum records, 1-100", required = true) int limit) {
        return audited("get_failed_payments", () -> payments.failedPayments(limit));
    }

    @McpTool(name = "get_outbox_status", title = "Get transactional outbox status", description = "Returns counts of pending, failed and published outbox events plus maximum retry attempts.", generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public Map<String, Object> getOutboxStatus() {
        return audited("get_outbox_status", payments::outboxStatus);
    }

    @McpTool(name = "get_ledger_entries", title = "Get ledger entries", description = "Returns ledger projection entries for a payment. Read-only.", generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public List<Map<String, Object>> getLedgerEntries(@McpToolParam(description = "Payment UUID", required = true) String paymentId) {
        return audited("get_ledger_entries", () -> payments.ledger(paymentId));
    }

    @McpTool(name = "get_webhook_queue", title = "Get webhook queue", description = "Returns recent webhook deliveries, optionally filtered by status. Use for delivery incident analysis.", generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public List<Map<String, Object>> getWebhookQueue(
            @McpToolParam(description = "Optional status such as PENDING", required = false) String status,
            @McpToolParam(description = "Maximum records, 1-100", required = true) int limit) {
        return audited("get_webhook_queue", () -> payments.webhooks(status, limit));
    }

    @McpTool(name = "get_incident_snapshot", title = "Get incident snapshot", description = "Returns a compact live operational snapshot of payments, transactional outbox and provider state. Use before forming incident conclusions.", generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public Map<String, Object> getIncidentSnapshot() {
        return audited("get_incident_snapshot", payments::incidentSnapshot);
    }

    private <T> T audited(String tool, Supplier<T> call) {
        long start = System.nanoTime();
        try {
            T value = call.get();
            metrics.counter("mcp_tool_calls_total", "tool", tool, "status", "success").increment();
            metrics.timer("mcp_tool_duration", "tool", tool).record(Duration.ofNanos(System.nanoTime() - start));
            log.info("event=mcp.tool.completed tool={} status=SUCCESS durationMs={}", tool, (System.nanoTime() - start) / 1_000_000);
            return value;
        } catch (RuntimeException ex) {
            metrics.counter("mcp_tool_calls_total", "tool", tool, "status", "error").increment();
            log.warn("event=mcp.tool.completed tool={} status=ERROR errorType={}", tool, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
