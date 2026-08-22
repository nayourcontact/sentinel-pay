package com.acme.payments.ops;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/internal/ops")
public class OpsQueryController {
    private final JdbcTemplate jdbc;
    private final String expectedKey;

    public OpsQueryController(JdbcTemplate jdbc, @Value("${payments.ops.internal-key:dev-ops-key}") String expectedKey) {
        this.jdbc = jdbc;
        this.expectedKey = expectedKey;
    }

    @GetMapping("/payments/{id}")
    public Map<String, Object> payment(@RequestHeader("X-Internal-Ops-Key") String key, @PathVariable UUID id) {
        authorize(key);
        return jdbc.query("""
                select id, merchant_reference, amount, currency, status, provider_reference,
                       failure_reason, created_at, updated_at, version
                from payments where id = ?
                """, rs -> {
            if (!rs.next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "payment not found");
            var out = new LinkedHashMap<String, Object>();
            out.put("id", rs.getObject("id").toString());
            out.put("merchantReference", rs.getString("merchant_reference"));
            out.put("amount", rs.getBigDecimal("amount").toPlainString());
            out.put("currency", rs.getString("currency"));
            out.put("status", rs.getString("status"));
            out.put("providerReference", rs.getString("provider_reference"));
            out.put("failureReason", rs.getString("failure_reason"));
            out.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
            out.put("updatedAt", rs.getTimestamp("updated_at").toInstant().toString());
            out.put("version", rs.getLong("version"));
            return out;
        }, id);
    }

    @GetMapping("/payments")
    public List<Map<String, Object>> payments(@RequestHeader("X-Internal-Ops-Key") String key,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "20") int limit) {
        authorize(key);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String sql = """
                select id, merchant_reference, amount, currency, status, provider_reference,
                       failure_reason, created_at, updated_at
                from payments
                """ + (status == null || status.isBlank() ? "" : " where status = ? ") +
                " order by created_at desc limit " + safeLimit;
        Object[] args = status == null || status.isBlank() ? new Object[]{} : new Object[]{status.toUpperCase(Locale.ROOT)};
        return jdbc.query(sql, (rs, rowNum) -> {
            var out = new LinkedHashMap<String, Object>();
            out.put("id", rs.getObject("id").toString());
            out.put("merchantReference", rs.getString("merchant_reference"));
            out.put("amount", rs.getBigDecimal("amount").toPlainString());
            out.put("currency", rs.getString("currency"));
            out.put("status", rs.getString("status"));
            out.put("providerReference", rs.getString("provider_reference"));
            out.put("failureReason", rs.getString("failure_reason"));
            out.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
            out.put("updatedAt", rs.getTimestamp("updated_at").toInstant().toString());
            return out;
        }, args);
    }

    @GetMapping("/payments/{id}/timeline")
    public Map<String, Object> timeline(@RequestHeader("X-Internal-Ops-Key") String key, @PathVariable UUID id) {
        authorize(key);
        var result = new LinkedHashMap<String, Object>();
        result.put("payment", payment(key, id));
        result.put("events", jdbc.query("""
                select id, event_type, created_at, published_at, failed_at, attempts, last_error
                from outbox_events where aggregate_id = ? order by created_at
                """, (rs, rowNum) -> Map.of(
                "id", rs.getObject("id").toString(),
                "eventType", rs.getString("event_type"),
                "createdAt", rs.getTimestamp("created_at").toInstant().toString(),
                "deliveryState", rs.getTimestamp("failed_at") != null ? "FAILED" : rs.getTimestamp("published_at") != null ? "PUBLISHED" : "PENDING",
                "attempts", rs.getInt("attempts"),
                "lastError", Objects.toString(rs.getString("last_error"), "")
        ), id));
        result.put("ledger", ledger(key, id));
        return result;
    }

    @GetMapping("/ledger/{paymentId}")
    public List<Map<String, Object>> ledger(@RequestHeader("X-Internal-Ops-Key") String key, @PathVariable UUID paymentId) {
        authorize(key);
        return jdbc.query("""
                select id, entry_type, amount, currency, created_at
                from ledger_entries where payment_id = ? order by created_at
                """, (rs, rowNum) -> Map.of(
                "id", rs.getObject("id").toString(),
                "entryType", rs.getString("entry_type"),
                "amount", rs.getBigDecimal("amount").toPlainString(),
                "currency", rs.getString("currency"),
                "createdAt", rs.getTimestamp("created_at").toInstant().toString()
        ), paymentId);
    }

    @GetMapping("/outbox/status")
    public Map<String, Object> outboxStatus(@RequestHeader("X-Internal-Ops-Key") String key) {
        authorize(key);
        return jdbc.queryForMap("""
                select
                  count(*) filter (where published_at is null and failed_at is null) as pending,
                  count(*) filter (where failed_at is not null) as failed,
                  count(*) filter (where published_at is not null) as published,
                  coalesce(max(attempts), 0) as max_attempts
                from outbox_events
                """);
    }

    @GetMapping("/webhooks")
    public List<Map<String, Object>> webhooks(@RequestHeader("X-Internal-Ops-Key") String key,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "20") int limit) {
        authorize(key);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String sql = "select id,event_id,event_type,status,attempts,next_attempt_at,created_at from webhook_deliveries" +
                (status == null || status.isBlank() ? "" : " where status = ?") +
                " order by created_at desc limit " + safeLimit;
        Object[] args = status == null || status.isBlank() ? new Object[]{} : new Object[]{status.toUpperCase(Locale.ROOT)};
        return jdbc.query(sql, (rs, rowNum) -> Map.of(
                "id", rs.getObject("id").toString(),
                "eventId", rs.getString("event_id"),
                "eventType", rs.getString("event_type"),
                "status", rs.getString("status"),
                "attempts", rs.getInt("attempts"),
                "nextAttemptAt", rs.getTimestamp("next_attempt_at").toInstant().toString(),
                "createdAt", rs.getTimestamp("created_at").toInstant().toString()
        ), args);
    }

    @GetMapping("/incident-snapshot")
    public Map<String, Object> incidentSnapshot(@RequestHeader("X-Internal-Ops-Key") String key) {
        authorize(key);
        Map<String, Object> payments = jdbc.queryForMap("""
                select count(*) as total,
                       count(*) filter (where status='AUTHORIZED') as authorized,
                       count(*) filter (where status='FAILED') as failed,
                       count(*) filter (where status='AUTHORIZATION_PENDING') as pending
                from payments
                """);
        var result = new LinkedHashMap<String, Object>();
        result.put("payments", payments);
        result.put("outbox", outboxStatus(key));
        result.put("provider", Map.of("name", "deterministic-mock-psp", "status", "UP", "mode", "deterministic-demo"));
        result.put("diagnosticNote", "Demo provider deterministically declines approximately 10% of merchant references; production provider telemetry would be sourced from OTel/PSP health APIs.");
        return result;
    }

    private void authorize(String supplied) {
        if (!Objects.equals(expectedKey, supplied)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid internal operations key");
        }
    }
}
