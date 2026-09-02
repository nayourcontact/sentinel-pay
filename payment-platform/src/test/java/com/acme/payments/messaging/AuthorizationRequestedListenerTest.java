package com.acme.payments.messaging;

import com.acme.payments.application.AuthorizationProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthorizationRequestedListener")
class AuthorizationRequestedListenerTest {

    @Mock
    private AuthorizationProcessor processor;

    private AuthorizationRequestedListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuthorizationRequestedListener(
                processor,
                new ObjectMapper()
        );
    }

    @Test
    void shouldExtractPaymentIdAndDelegateToProcessor() throws Exception {
        UUID paymentId = UUID.randomUUID();

        String payload =
                "{\"paymentId\":\"" + paymentId + "\"}";

        listener.onMessage(payload);

        verify(processor).authorize(paymentId);
        verifyNoMoreInteractions(processor);
    }

    @Test
    void shouldRejectMalformedJsonWithoutInvokingProcessor() {
        assertThatThrownBy(
                () -> listener.onMessage("{invalid-json")
        ).isInstanceOf(Exception.class);

        verifyNoInteractions(processor);
    }

    @Test
    void shouldRejectInvalidPaymentIdWithoutInvokingProcessor() {
        String payload =
                "{\"paymentId\":\"not-a-uuid\"}";

        assertThatThrownBy(
                () -> listener.onMessage(payload)
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(processor);
    }
}