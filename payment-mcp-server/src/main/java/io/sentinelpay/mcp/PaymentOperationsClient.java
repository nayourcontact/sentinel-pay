package io.sentinelpay.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class PaymentOperationsClient {
    private final RestClient client;
    private final String internalKey;

    public PaymentOperationsClient(
            RestClient.Builder builder,
            @Value("${sentinelpay.payment-api.base-url:http://localhost:8080}") String baseUrl,
            @Value("${sentinelpay.payment-api.internal-key:dev-ops-key}") String internalKey
    ) {
        this.client = builder
                .baseUrl(baseUrl)
                .build();

        this.internalKey = internalKey;
    }

    public Map<String, Object> payment(String id) { return getMap("/internal/ops/payments/" + id); }
    public Map<String, Object> timeline(String id) { return getMap("/internal/ops/payments/" + id + "/timeline"); }
    public Map<String, Object> outboxStatus() { return getMap("/internal/ops/outbox/status"); }
    public Map<String, Object> incidentSnapshot() { return getMap("/internal/ops/incident-snapshot"); }
    public List<Map<String, Object>> ledger(String id) { return getList("/internal/ops/ledger/" + id); }
    public List<Map<String, Object>> failedPayments(int limit) { return getList("/internal/ops/payments?status=FAILED&limit=" + clamp(limit)); }
    public List<Map<String, Object>> webhooks(String status, int limit) {
        String path = "/internal/ops/webhooks?limit=" + clamp(limit);
        if (status != null && !status.isBlank()) path += "&status=" + status;
        return getList(path);
    }

    private Map<String, Object> getMap(String path) {
        return client.get().uri(path).header("X-Internal-Ops-Key", internalKey).retrieve().body(new ParameterizedTypeReference<>() {});
    }

    private List<Map<String, Object>> getList(String path) {
        return client.get().uri(path).header("X-Internal-Ops-Key", internalKey).retrieve().body(new ParameterizedTypeReference<>() {});
    }

    private static int clamp(int limit) { return Math.max(1, Math.min(limit, 100)); }
}
