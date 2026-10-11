package io.sentinelpay.mcp.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class RestClientConfig {
    @Bean
    public RestClient.Builder restClientBuilder(
            ObservationRegistry observationRegistry,
            @Value("${sentinelpay.payment-api.connect-timeout:1s}") Duration connectTimeout,
            @Value("${sentinelpay.payment-api.read-timeout:2s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory).observationRegistry(observationRegistry);
    }
}
