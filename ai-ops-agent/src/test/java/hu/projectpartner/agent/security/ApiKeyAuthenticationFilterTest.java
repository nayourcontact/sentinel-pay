package hu.projectpartner.agent.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyAuthenticationFilterTest {
    @AfterEach
    void reset() { SecurityContextHolder.clearContext(); }

    @Test
    void validKeyAuthenticates() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "expected-test-key");
        new ApiKeyAuthenticationFilter("expected-test-key")
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void invalidKeyDoesNotAuthenticate() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "wrong-key");
        new ApiKeyAuthenticationFilter("expected-test-key")
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
