package io.sentinelpay.mcp;

import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class PaymentServiceTokenProviderTest {
    @Test
    void disabledModeAddsNoBearerHeader() {
        var provider = new PaymentServiceTokenProvider(false, "", "", "", new ObjectMapper());
        var headers = new HttpHeaders();
        provider.apply(headers);
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION) != null).isFalse();
    }

    @Test
    void enabledModeRequiresCredentialsAndSafeEndpoint() {
        assertThatThrownBy(() -> new PaymentServiceTokenProvider(true, "", "id", "secret", new ObjectMapper()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentServiceTokenProvider(true, "http://example.com/token", "id", "secret", new ObjectMapper()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retrievesAndCachesServiceToken() throws Exception {
        var calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(form).contains("grant_type=client_credentials", "client_id=mcp", "client_secret=secret");
            byte[] payload = "{\"access_token\":\"machine-token\",\"expires_in\":300}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (var out = exchange.getResponseBody()) { out.write(payload); }
        });
        server.start();
        try {
            var provider = new PaymentServiceTokenProvider(true,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/token",
                    "mcp", "secret", new ObjectMapper());
            var one = new HttpHeaders();
            var two = new HttpHeaders();
            provider.apply(one);
            provider.apply(two);
            assertThat(one.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer machine-token");
            assertThat(two.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer machine-token");
            assertThat(calls.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test
    void refreshesTokenWhenExpiryIsNear() throws Exception {
        var calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            int count = calls.incrementAndGet();
            byte[] payload = ("{\"access_token\":\"token-" + count + "\",\"expires_in\":5}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (var out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        try {
            var provider = new PaymentServiceTokenProvider(true,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/token",
                    "mcp", "secret", new ObjectMapper());

            var first = new HttpHeaders();
            var second = new HttpHeaders();
            provider.apply(first);
            provider.apply(second);

            assertThat(first.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token-1");
            assertThat(second.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token-2");
            assertThat(calls.get()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }
}
