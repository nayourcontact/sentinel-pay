package com.acme.payments.messaging;

import com.acme.payments.application.AuthorizationProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthorizationRequestedListener {
    private final AuthorizationProcessor processor;
    private final ObjectMapper mapper;

    public AuthorizationRequestedListener(AuthorizationProcessor processor, ObjectMapper mapper) {
        this.processor = processor;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "payment.authorization.requested", groupId = "payment-orchestrator")
    public void onMessage(String payload) throws Exception {
        JsonNode json = mapper.readTree(payload);
        processor.authorize(UUID.fromString(json.get("paymentId").asText()));
    }
}
