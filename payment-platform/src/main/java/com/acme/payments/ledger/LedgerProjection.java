package com.acme.payments.ledger;

import com.acme.payments.application.PaymentRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class LedgerProjection {
    private final LedgerRepository ledger;
    private final PaymentRepository payments;
    private final ObjectMapper mapper;

    public LedgerProjection(LedgerRepository ledger, PaymentRepository payments, ObjectMapper mapper) {
        this.ledger = ledger;
        this.payments = payments;
        this.mapper = mapper;
    }

    @Transactional
    @KafkaListener(topics = "payment.authorized", groupId = "ledger-projection")
    public void onAuthorized(String payload) throws Exception {
        JsonNode json = mapper.readTree(payload);

        UUID paymentId = UUID.fromString(json.get("paymentId").asText());

        if (ledger.existsByPaymentIdAndEntryType(paymentId, "AUTHORIZATION")) {
            return;
        }

        var payment = payments.findById(paymentId)
                .orElseThrow();

        var entry = new LedgerEntryEntity(
                UUID.randomUUID(),
                paymentId,
                "AUTHORIZATION",
                payment.money().amount(),
                payment.money().currency().getCurrencyCode(),
                Instant.now()
        );

        ledger.save(entry);
    }
}
