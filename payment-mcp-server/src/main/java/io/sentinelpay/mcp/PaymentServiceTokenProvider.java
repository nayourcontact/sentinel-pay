package io.sentinelpay.mcp;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Separate machine identity for read-only Payment Operations API calls. Never log access tokens. */
@Component
public class PaymentServiceTokenProvider {
    private final boolean enabled;
    private final URI tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient http;
    private final ObjectMapper json;
    private String accessToken;
    private Instant expiresAt = Instant.EPOCH;

    @Autowired
    public PaymentServiceTokenProvider(
            @Value("${sentinelpay.payment-api.oauth2.enabled:false}") boolean enabled,
            @Value("${sentinelpay.payment-api.oauth2.token-uri:}") String tokenUri,
            @Value("${sentinelpay.payment-api.oauth2.client-id:}") String clientId,
            @Value("${sentinelpay.payment-api.oauth2.client-secret:}") String clientSecret,
            ObjectMapper json) {
        this(enabled, tokenUri, clientId, clientSecret, json,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    // Package-private for isolated tests; no shared/global RestClient state.
    PaymentServiceTokenProvider(boolean enabled, String tokenUri, String clientId, String clientSecret,
                                ObjectMapper json, HttpClient http) {
        this.enabled = enabled;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.json = Objects.requireNonNull(json);
        this.http = Objects.requireNonNull(http);
        if (enabled) {
            if (tokenUri == null || tokenUri.isBlank() || clientId == null || clientId.isBlank()
                    || clientSecret == null || clientSecret.isBlank()) {
                throw new IllegalArgumentException("OAuth2 client credentials must be set when enabled");
            }
            URI uri = URI.create(tokenUri);
            String host = uri.getHost();
            boolean local = host != null && (host.equals("localhost") || host.equals("127.0.0.1")
                    || host.equals("keycloak") || host.equals("host.docker.internal"));
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    && !("http".equalsIgnoreCase(uri.getScheme()) && local)) {
                throw new IllegalArgumentException("OAuth2 token URI must be HTTPS (except local development hosts)");
            }
            this.tokenUri = uri;
        } else {
            this.tokenUri = null;
        }
    }

    public void apply(HttpHeaders headers) {
        if (enabled) headers.setBearerAuth(token());
    }

    synchronized String token() {
        if (!enabled) throw new IllegalStateException("OAuth2 is disabled");
        if (accessToken != null && Instant.now().isBefore(expiresAt.minusSeconds(10))) return accessToken;
        String form = "grant_type=client_credentials&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret);
        HttpRequest request = HttpRequest.newBuilder(tokenUri)
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("OAuth2 token endpoint returned HTTP " + response.statusCode());
            }
            JsonNode body = json.readTree(response.body());
            String newToken = body.path("access_token").asText("");
            long ttl = body.path("expires_in").asLong(0);
            if (newToken.isBlank() || ttl <= 0) throw new IllegalStateException("OAuth2 token response missing token/expiry");
            // Fail closed on errors; never fall back to internal-key-only calls in JWT mode.
            accessToken = newToken;
            expiresAt = Instant.now().plusSeconds(ttl);
            return accessToken;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OAuth2 token request interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("OAuth2 token request failed", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
