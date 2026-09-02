package com.acme.payments.webhook;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookProjection")
class WebhookProjectionTest {

    @Mock
    private WebhookRepository repository;

    private WebhookProjection projection;

    @BeforeEach
    void setUp() {
        projection = new WebhookProjection(repository);
    }

    @Test
    void shouldEnqueueAuthorizedEvent() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        "payment.authorized",
                        2,
                        42L,
                        "payment-1",
                        "{\"paymentId\":\"123\"}"
                );

        when(
                repository.existsByEventId(
                        "payment.authorized:2:42"
                )
        ).thenReturn(false);

        projection.enqueue(record);

        ArgumentCaptor<WebhookDeliveryEntity> captor =
                ArgumentCaptor.forClass(WebhookDeliveryEntity.class);

        verify(repository).save(captor.capture());

        WebhookDeliveryEntity delivery = captor.getValue();

        assertThat(delivery.id).isNotNull();
        assertThat(delivery.eventId)
                .isEqualTo("payment.authorized:2:42");

        assertThat(delivery.eventType)
                .isEqualTo("payment.authorized");

        assertThat(delivery.payload)
                .isEqualTo("{\"paymentId\":\"123\"}");

        assertThat(delivery.status).isEqualTo("PENDING");
        assertThat(delivery.attempts).isZero();
        assertThat(delivery.nextAttemptAt).isNotNull();
        assertThat(delivery.createdAt).isNotNull();
    }

    @Test
    void shouldEnqueueFailedEvent() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        "payment.failed",
                        0,
                        10L,
                        null,
                        "{\"reason\":\"declined\"}"
                );

        when(
                repository.existsByEventId(
                        "payment.failed:0:10"
                )
        ).thenReturn(false);

        projection.enqueue(record);

        ArgumentCaptor<WebhookDeliveryEntity> captor =
                ArgumentCaptor.forClass(WebhookDeliveryEntity.class);

        verify(repository).save(captor.capture());

        assertThat(captor.getValue().eventType)
                .isEqualTo("payment.failed");
    }

    @Test
    void shouldIgnoreAlreadyProcessedKafkaRecord() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        "payment.authorized",
                        1,
                        123L,
                        null,
                        "{}"
                );

        when(
                repository.existsByEventId(
                        "payment.authorized:1:123"
                )
        ).thenReturn(true);

        projection.enqueue(record);

        verify(repository)
                .existsByEventId("payment.authorized:1:123");

        verify(repository, never()).save(any());
    }

    @Test
    void shouldUseTopicPartitionAndOffsetAsEventIdentity() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        "payment.failed",
                        5,
                        999L,
                        "key",
                        "payload"
                );

        when(
                repository.existsByEventId(
                        "payment.failed:5:999"
                )
        ).thenReturn(false);

        projection.enqueue(record);

        verify(repository)
                .existsByEventId("payment.failed:5:999");
    }
}