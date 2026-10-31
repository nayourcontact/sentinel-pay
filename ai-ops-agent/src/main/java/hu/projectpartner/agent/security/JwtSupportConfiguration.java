package hu.projectpartner.agent.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import java.util.LinkedHashSet;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.security.jwt.enabled", havingValue = "true")
public class JwtSupportConfiguration {
    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.jwt.jwk-set-uri}") String jwkSetUri,
                          @Value("${app.security.jwt.issuer-uri}") String issuer,
                          @Value("${app.security.jwt.audience}") String audience) {
        if (!jwkSetUri.startsWith("https://") && !jwkSetUri.startsWith("http://localhost:")
                && !jwkSetUri.startsWith("http://127.0.0.1:")
                && !jwkSetUri.startsWith("http://keycloak:")
                && !jwkSetUri.startsWith("http://host.docker.internal:")) {
            throw new IllegalArgumentException("JWT JWK endpoint must be HTTPS (or an explicitly local development host)");
        }
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT audience must be configured in secure mode");
        }
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "JWT audience mismatch", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var roles = new LinkedHashSet<GrantedAuthority>();
            // Dedicated roles claim; never trust client-supplied headers as authorities.
            Object claim = jwt.getClaims().get("roles");
            if (claim instanceof Collection<?> values) {
                for (Object value : values) {
                    if (value instanceof String role && role.matches("[A-Z][A-Z0-9_]*")) {
                        roles.add(new SimpleGrantedAuthority("ROLE_" + role));
                    }
                }
            }
            return roles;
        });
        return converter;
    }
}
