package com.acme.payments.adapter.out.persistence;

import com.acme.payments.application.PaymentRepository;
import com.acme.payments.domain.Money;
import com.acme.payments.domain.Payment;
import com.acme.payments.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false"
})
@Testcontainers
@DisplayName("Kafka authorization integration")
class KafkaAuthorizationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("payments")
                    .withUsername("payments")
                    .withPassword("payments");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(
                    DockerImageName.parse(
                            "apache/kafka:4.3.1"
                    )
            );

    @DynamicPropertySource
    static void properties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );

        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );

        registry.add(
                "spring.kafka.bootstrap-servers",
                KAFKA::getBootstrapServers
        );
    }

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Test
    void authorizationRequestedEventShouldBeConsumed() {
        /*
         * Olyan reference-et választunk,
         * amelyik nem decline bucket.
         */
        String merchantReference =
                findAuthorizedReference();

        Payment payment = Payment.create(
                merchantReference,
                Money.of("149.90", "EUR")
        );

        payment.requestAuthorization();

        payments.save(payment);

        String payload =
                "{\"paymentId\":\""
                        + payment.id()
                        + "\"}";

        kafka.send(
                "payment.authorization.requested",
                payment.id().toString(),
                payload
        );

        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {

                    Payment reloaded =
                            payments.findById(payment.id())
                                    .orElseThrow();

                    assertThat(reloaded.status())
                            .isEqualTo(
                                    PaymentStatus.AUTHORIZED
                            );

                    assertThat(
                            reloaded.providerReference()
                    ).startsWith("mock_");
                });
    }

    private String findAuthorizedReference() {
        for (int i = 0; i < 10000; i++) {
            String candidate =
                    "kafka-authorized-" + i;

            int bucket =
                    Math.floorMod(
                            candidate.hashCode(),
                            10
                    );

            if (bucket != 0) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "Could not find authorized reference"
        );
    }
}