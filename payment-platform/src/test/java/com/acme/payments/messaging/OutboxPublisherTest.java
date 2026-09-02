package com.acme.payments.messaging;

import com.acme.payments.adapter.out.persistence.OutboxPublishingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("OutboxPublisher")
class OutboxPublisherTest {

    @Test
    void shouldDelegateScheduledPublishingToService() {
        OutboxPublishingService service =
                mock(OutboxPublishingService.class);

        OutboxPublisher publisher =
                new OutboxPublisher(service);

        publisher.publish();

        verify(service).publishBatch();
        verifyNoMoreInteractions(service);
    }
}