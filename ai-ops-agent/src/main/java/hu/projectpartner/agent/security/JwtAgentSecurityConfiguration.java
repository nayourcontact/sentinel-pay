package hu.projectpartner.agent.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.security.jwt.enabled", havingValue = "true")
public class JwtAgentSecurityConfiguration {
    @Bean
    SecurityFilterChain jwtAgentSecurity(HttpSecurity http, ApiKeyAuthenticationFilter apiKeyFilter,
                                         JwtAuthenticationConverter converter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**").hasRole("OPS_READER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/knowledge", "/api/v1/knowledge/files").hasAnyRole("KNOWLEDGE_WRITER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/chat").hasAnyRole("AGENT_USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/knowledge/search").hasAnyRole("AGENT_USER", "KNOWLEDGE_WRITER", "ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }
}
