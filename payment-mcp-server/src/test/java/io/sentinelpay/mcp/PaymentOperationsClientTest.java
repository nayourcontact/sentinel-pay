package io.sentinelpay.mcp;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PaymentOperationsClientTest {
    private record Fixture(PaymentOperationsClient client, MockRestServiceServer server) {}

    private Fixture fixture(int attempts, int minimumCalls) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentOperationsClient client = new PaymentOperationsClient(builder, "http://localhost:8080", "test-key",
                attempts, Duration.ZERO, 50f, minimumCalls, 10, Duration.ofMinutes(1));
        return new Fixture(client, server);
    }

    @Test
    void successfulReadUsesInternalAuthenticationHeader() {
        Fixture f = fixture(3, 5);
        f.server.expect(requestTo("http://localhost:8080/internal/ops/outbox/status"))
                .andExpect(header("X-Internal-Ops-Key", "test-key"))
                .andRespond(withSuccess("{\"pending\":2}", MediaType.APPLICATION_JSON));
        assertThat(f.client.outboxStatus()).containsEntry("pending", 2);
        f.server.verify();
    }

    @Test
    void retriesTransientServerErrorsAndRecovers() {
        Fixture f = fixture(3, 5);
        String url = "http://localhost:8080/internal/ops/outbox/status";
        f.server.expect(requestTo(url)).andRespond(withServerError());
        f.server.expect(requestTo(url)).andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
        assertThat(f.client.outboxStatus()).containsEntry("ok", true);
        assertThat(f.client.retry().getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(1);
        f.server.verify();
    }

    @Test
    void doesNotRetryBusinessErrors() {
        Fixture f = fixture(3, 5);
        f.server.expect(requestTo("http://localhost:8080/internal/ops/outbox/status"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(f.client::outboxStatus).isInstanceOf(HttpClientErrorException.BadRequest.class);
        f.server.verify();
    }

    @Test
    void opensCircuitAfterRepeatedOutageAndFailsFast() {
        Fixture f = fixture(1, 2);
        String url = "http://localhost:8080/internal/ops/outbox/status";
        f.server.expect(requestTo(url)).andRespond(withServerError());
        f.server.expect(requestTo(url)).andRespond(withServerError());
        assertThatThrownBy(f.client::outboxStatus).isInstanceOf(HttpServerErrorException.class);
        assertThatThrownBy(f.client::outboxStatus).isInstanceOf(HttpServerErrorException.class);
        assertThat(f.client.circuitBreaker().getState()).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);
        assertThatThrownBy(f.client::outboxStatus).isInstanceOf(CallNotPermittedException.class);
        f.server.verify();
    }

    @Test
    void transientFailureClassificationDoesNotRetryOtherErrors() {
        assertThat(PaymentOperationsClient.isTransientFailure(new ResourceAccessException("timeout"))).isTrue();
        assertThat(PaymentOperationsClient.isTransientFailure(new IllegalArgumentException("bad id"))).isFalse();
        assertThat(PaymentOperationsClient.isTransientFailure(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "rate", null, null, null))).isTrue();
    }
}
