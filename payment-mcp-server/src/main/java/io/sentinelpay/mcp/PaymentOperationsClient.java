package io.sentinelpay.mcp;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpClientErrorException;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class PaymentOperationsClient {
    private final RestClient client;
    private final String internalKey;
    private final PaymentServiceTokenProvider tokenProvider;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public PaymentOperationsClient(
            RestClient.Builder builder,
            PaymentServiceTokenProvider tokenProvider,
            @Value("${sentinelpay.payment-api.base-url:http://localhost:8080}") String baseUrl,
            @Value("${sentinelpay.payment-api.internal-key:dev-ops-key}") String internalKey,
            @Value("${sentinelpay.payment-api.retry.max-attempts:3}") int maxAttempts,
            @Value("${sentinelpay.payment-api.retry.wait-duration:200ms}") Duration waitDuration,
            @Value("${sentinelpay.payment-api.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${sentinelpay.payment-api.circuit-breaker.minimum-calls:5}") int minimumCalls,
            @Value("${sentinelpay.payment-api.circuit-breaker.sliding-window-size:10}") int slidingWindowSize,
            @Value("${sentinelpay.payment-api.circuit-breaker.open-duration:10s}") Duration openDuration
    ) {
        this.client = builder
                .baseUrl(baseUrl)
                .build();

        this.internalKey = internalKey;
        this.tokenProvider = tokenProvider;
        this.circuitBreaker = CircuitBreaker.of("paymentOperationsApi", CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .minimumNumberOfCalls(minimumCalls)
                .slidingWindowSize(slidingWindowSize)
                .waitDurationInOpenState(openDuration)
                .recordException(PaymentOperationsClient::isTransientFailure)
                .build());
        this.retry = Retry.of("paymentOperationsApi", RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(waitDuration)
                .retryOnException(PaymentOperationsClient::isTransientFailure)
                .build());
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
        return protectedGet(() -> client.get().uri(path).header("X-Internal-Ops-Key", internalKey)
                .headers(headers -> tokenProvider.apply(headers))
                .retrieve().body(new ParameterizedTypeReference<>() {}));
    }

    private List<Map<String, Object>> getList(String path) {
        return protectedGet(() -> client.get().uri(path).header("X-Internal-Ops-Key", internalKey)
                .headers(headers -> tokenProvider.apply(headers))
                .retrieve().body(new ParameterizedTypeReference<>() {}));
    }

    private <T> T protectedGet(Supplier<T> call) {
        // Retry wraps the circuit breaker: each attempted network call contributes to breaker metrics.
        // Never retry authorization/payment writes with this configuration.
        return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call)).get();
    }

    static boolean isTransientFailure(Throwable error) {
        return error instanceof ResourceAccessException
                || error instanceof HttpServerErrorException
                || (error instanceof HttpClientErrorException clientError
                    && clientError.getStatusCode().value() == 429);
    }

    CircuitBreaker circuitBreaker() { return circuitBreaker; }
    Retry retry() { return retry; }

    private static int clamp(int limit) { return Math.max(1, Math.min(limit, 100)); }
}
