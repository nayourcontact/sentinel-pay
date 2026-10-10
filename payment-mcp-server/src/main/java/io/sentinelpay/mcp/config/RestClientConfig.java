package io.sentinelpay.mcp.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class RestClientConfig {
    @Bean
    public RestClient.Builder restClientBuilder(ObservationRegistry observationRegistry) {
        return RestClient.builder().observationRegistry(observationRegistry);
    }
}
