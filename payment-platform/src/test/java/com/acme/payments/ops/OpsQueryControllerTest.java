package com.acme.payments.ops;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OpsQueryController")
class OpsQueryControllerTest {

    private static final String VALID_KEY = "test-ops-key";

    @Mock
    private JdbcTemplate jdbc;

    private OpsQueryController controller;

    @BeforeEach
    void setUp() {
        controller = new OpsQueryController(jdbc, VALID_KEY);
    }

    @Nested
    @DisplayName("authorization")
    class Authorization {

        @Test
        void shouldRejectInvalidInternalKey() {
            UUID paymentId = UUID.randomUUID();

            assertThatThrownBy(
                    () -> controller.payment("wrong-key", paymentId)
            )
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException response =
                                (ResponseStatusException) ex;

                        assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN);

                        assertThat(response.getReason())
                                .isEqualTo("invalid internal operations key");
                    });

            verifyNoInteractions(jdbc);
        }

        @Test
        void shouldRejectNullInternalKey() {
            UUID paymentId = UUID.randomUUID();

            assertThatThrownBy(
                    () -> controller.payment(null, paymentId)
            )
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException response =
                                (ResponseStatusException) ex;

                        assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN);
                    });

            verifyNoInteractions(jdbc);
        }
    }

    @Nested
    @DisplayName("payment")
    class PaymentQuery {

        @Test
        void shouldReturnMappedPayment() throws Exception {
            UUID paymentId = UUID.randomUUID();

            ResultSet rs = mock(ResultSet.class);

            when(rs.next()).thenReturn(true);
            when(rs.getObject("id")).thenReturn(paymentId);
            when(rs.getString("merchant_reference"))
                    .thenReturn("order-42");
            when(rs.getBigDecimal("amount"))
                    .thenReturn(new BigDecimal("149.90"));
            when(rs.getString("currency"))
                    .thenReturn("EUR");
            when(rs.getString("status"))
                    .thenReturn("AUTHORIZED");
            when(rs.getString("provider_reference"))
                    .thenReturn("provider-123");
            when(rs.getString("failure_reason"))
                    .thenReturn(null);
            when(rs.getTimestamp("created_at"))
                    .thenReturn(Timestamp.from(
                            Instant.parse("2026-01-01T10:00:00Z")
                    ));
            when(rs.getTimestamp("updated_at"))
                    .thenReturn(Timestamp.from(
                            Instant.parse("2026-01-01T10:01:00Z")
                    ));
            when(rs.getLong("version"))
                    .thenReturn(3L);

            stubQueryWithExtractor(rs);

            Map<String, Object> result =
                    controller.payment(VALID_KEY, paymentId);

            assertThat(result)
                    .containsEntry("id", paymentId.toString())
                    .containsEntry("merchantReference", "order-42")
                    .containsEntry("amount", "149.90")
                    .containsEntry("currency", "EUR")
                    .containsEntry("status", "AUTHORIZED")
                    .containsEntry("providerReference", "provider-123")
                    .containsEntry("failureReason", null)
                    .containsEntry(
                            "createdAt",
                            "2026-01-01T10:00:00Z"
                    )
                    .containsEntry(
                            "updatedAt",
                            "2026-01-01T10:01:00Z"
                    )
                    .containsEntry("version", 3L);

            verify(jdbc).query(
                    contains("from payments where id = ?"),
                    any(ResultSetExtractor.class),
                    eq(paymentId)
            );
        }

        @Test
        void shouldReturnNotFoundWhenPaymentDoesNotExist()
                throws Exception {

            UUID paymentId = UUID.randomUUID();

            ResultSet rs = mock(ResultSet.class);

            when(rs.next()).thenReturn(false);

            stubQueryWithExtractor(rs);

            assertThatThrownBy(
                    () -> controller.payment(VALID_KEY, paymentId)
            )
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException response =
                                (ResponseStatusException) ex;

                        assertThat(response.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND);

                        assertThat(response.getReason())
                                .isEqualTo("payment not found");
                    });
        }
    }

    @Nested
    @DisplayName("payments")
    class PaymentsQuery {

        @Test
        void shouldQueryWithoutStatusFilter() {
            when(jdbc.query(
                    anyString(),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenReturn(List.of());

            controller.payments(
                    VALID_KEY,
                    null,
                    20
            );

            ArgumentCaptor<String> sqlCaptor =
                    ArgumentCaptor.forClass(String.class);

            ArgumentCaptor<Object[]> argsCaptor =
                    ArgumentCaptor.forClass(Object[].class);

            verify(jdbc).query(
                    sqlCaptor.capture(),
                    any(RowMapper.class),
                    argsCaptor.capture()
            );

            assertThat(sqlCaptor.getValue())
                    .doesNotContain("where status = ?")
                    .contains("limit 20");

            assertThat(argsCaptor.getValue()).isEmpty();
        }

        @Test
        void shouldQueryWithoutStatusFilterWhenStatusIsBlank() {
            when(jdbc.query(
                    anyString(),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenReturn(List.of());

            controller.payments(
                    VALID_KEY,
                    "   ",
                    20
            );

            ArgumentCaptor<String> sqlCaptor =
                    ArgumentCaptor.forClass(String.class);

            verify(jdbc).query(
                    sqlCaptor.capture(),
                    any(RowMapper.class),
                    any(Object[].class)
            );

            assertThat(sqlCaptor.getValue())
                    .doesNotContain("where status = ?");
        }

        @Test
        void shouldQueryWithUppercaseStatusFilter() {
            when(jdbc.query(
                    anyString(),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenReturn(List.of());

            controller.payments(
                    VALID_KEY,
                    "authorized",
                    20
            );

            ArgumentCaptor<String> sqlCaptor =
                    ArgumentCaptor.forClass(String.class);

            ArgumentCaptor<Object[]> argsCaptor =
                    ArgumentCaptor.forClass(Object[].class);

            verify(jdbc).query(
                    sqlCaptor.capture(),
                    any(RowMapper.class),
                    argsCaptor.capture()
            );

            assertThat(sqlCaptor.getValue())
                    .contains("where status = ?")
                    .contains("limit 20");

            assertThat(argsCaptor.getValue())
                    .containsExactly("AUTHORIZED");
        }

        @Test
        void shouldClampLimitToMinimumOne() {
            when(jdbc.query(
                    anyString(),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenReturn(List.of());

            controller.payments(
                    VALID_KEY,
                    null,
                    -100
            );

            ArgumentCaptor<String> sqlCaptor =
                    ArgumentCaptor.forClass(String.class);

            verify(jdbc).query(
                    sqlCaptor.capture(),
                    any(RowMapper.class),
                    any(Object[].class)
            );

            assertThat(sqlCaptor.getValue())
                    .contains("limit 1");
        }

        @Test
        void shouldClampLimitToMaximumHundred() {
            when(jdbc.query(
                    anyString(),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenReturn(List.of());

            controller.payments(
                    VALID_KEY,
                    null,
                    500
            );

            ArgumentCaptor<String> sqlCaptor =
                    ArgumentCaptor.forClass(String.class);

            verify(jdbc).query(
                    sqlCaptor.capture(),
                    any(RowMapper.class),
                    any(Object[].class)
            );

            assertThat(sqlCaptor.getValue())
                    .contains("limit 100");
        }

        @Test
        void shouldMapPaymentRows() throws Exception {
            UUID paymentId = UUID.randomUUID();

            ResultSet rs = mock(ResultSet.class);

            when(rs.getObject("id"))
                    .thenReturn(paymentId);
            when(rs.getString("merchant_reference"))
                    .thenReturn("order-55");
            when(rs.getBigDecimal("amount"))
                    .thenReturn(new BigDecimal("25.00"));
            when(rs.getString("currency"))
                    .thenReturn("USD");
            when(rs.getString("status"))
                    .thenReturn("FAILED");
            when(rs.getString("provider_reference"))
                    .thenReturn(null);
            when(rs.getString("failure_reason"))
                    .thenReturn("declined");
            when(rs.getTimestamp("created_at"))
                    .thenReturn(Timestamp.from(
                            Instant.parse("2026-01-01T10:00:00Z")
                    ));
            when(rs.getTimestamp("updated_at"))
                    .thenReturn(Timestamp.from(
                            Instant.parse("2026-01-01T10:02:00Z")
                    ));

            stubQueryWithRowMapper(rs);

            List<Map<String, Object>> result =
                    controller.payments(
                            VALID_KEY,
                            null,
                            20
                    );

            assertThat(result).hasSize(1);

            assertThat(result.get(0))
                    .containsEntry("id", paymentId.toString())
                    .containsEntry("merchantReference", "order-55")
                    .containsEntry("amount", "25.00")
                    .containsEntry("currency", "USD")
                    .containsEntry("status", "FAILED")
                    .containsEntry("providerReference", null)
                    .containsEntry("failureReason", "declined");
        }
    }

    @Nested
    @DisplayName("ledger")
    class LedgerQuery {

        @Test
        void shouldMapLedgerEntries() throws Exception {
            UUID paymentId = UUID.randomUUID();
            UUID ledgerId = UUID.randomUUID();

            ResultSet rs = mock(ResultSet.class);

            when(rs.getObject("id"))
                    .thenReturn(ledgerId);

            when(rs.getString("entry_type"))
                    .thenReturn("AUTHORIZATION");

            when(rs.getBigDecimal("amount"))
                    .thenReturn(new BigDecimal("149.90"));

            when(rs.getString("currency"))
                    .thenReturn("EUR");

            when(rs.getTimestamp("created_at"))
                    .thenReturn(Timestamp.from(
                            Instant.parse("2026-01-01T10:00:00Z")
                    ));

            stubQueryWithRowMapper(rs);

            List<Map<String, Object>> result =
                    controller.ledger(
                            VALID_KEY,
                            paymentId
                    );

            assertThat(result).hasSize(1);

            assertThat(result.get(0))
                    .containsEntry("id", ledgerId.toString())
                    .containsEntry(
                            "entryType",
                            "AUTHORIZATION"
                    )
                    .containsEntry("amount", "149.90")
                    .containsEntry("currency", "EUR")
                    .containsEntry(
                            "createdAt",
                            "2026-01-01T10:00:00Z"
                    );
        }
    }

    @Nested
    @DisplayName("outbox status")
    class OutboxStatus {

        @Test
        void shouldReturnOutboxStatus() {
            Map<String, Object> status = Map.of(
                    "pending", 5L,
                    "failed", 1L,
                    "published", 20L,
                    "max_attempts", 3
            );

            when(jdbc.queryForMap(
                    contains("from outbox_events")
            )).thenReturn(status);

            Map<String, Object> result =
                    controller.outboxStatus(VALID_KEY);

            assertThat(result).isSameAs(status);
        }
    }

    @Nested
    @DisplayName("webhooks")
    class WebhooksQuery {

        @Test
        void shouldQueryWithoutStatusFilter() {
            when(jdbc.query(
                    anyString(),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenReturn(List.of());

            controller.webhooks(
                    VALID_KEY,
                    null,
                    20
            );

            ArgumentCaptor<String> sqlCaptor =
                    ArgumentCaptor.forClass(String.class);

            ArgumentCaptor<Object[]> argsCaptor =
                    ArgumentCaptor.forClass(Object[].class);

            verify(jdbc).query(
                    sqlCaptor.capture(),
                    any(RowMapper.class),
                    argsCaptor.capture()
            );

            assertThat(sqlCaptor.getValue())
                    .doesNotContain("where status = ?")
                    .contains("limit 20");

            assertThat(argsCaptor.getValue()).isEmpty();
        }

        @Test
        void shouldQueryWithoutStatusFilterWhenStatusIsBlank() {
            when(jdbc.query(
                    anyString(),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenReturn(List.of());

            controller.webhooks(
                    VALID_KEY,
                    " ",
                    20
            );

            ArgumentCaptor<String> sqlCaptor =
                    ArgumentCaptor.forClass(String.class);

            verify(jdbc).query(
                    sqlCaptor.capture(),
                    any(RowMapper.class),
                    any(Object[].class)
            );

            assertThat(sqlCaptor.getValue())
                    .doesNotContain("where status = ?");
        }

        @Test
        void shouldQueryWithUppercaseStatusFilter() {
            when(jdbc.query(
                    anyString(),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenReturn(List.of());

            controller.webhooks(
                    VALID_KEY,
                    "failed",
                    20
            );

            ArgumentCaptor<String> sqlCaptor =
                    ArgumentCaptor.forClass(String.class);

            ArgumentCaptor<Object[]> argsCaptor =
                    ArgumentCaptor.forClass(Object[].class);

            verify(jdbc).query(
                    sqlCaptor.capture(),
                    any(RowMapper.class),
                    argsCaptor.capture()
            );

            assertThat(sqlCaptor.getValue())
                    .contains("where status = ?")
                    .contains("limit 20");

            assertThat(argsCaptor.getValue())
                    .containsExactly("FAILED");
        }

        @Test
        void shouldClampLimitToMinimumOne() {
            when(jdbc.query(
                    anyString(),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenReturn(List.of());

            controller.webhooks(
                    VALID_KEY,
                    null,
                    0
            );

            ArgumentCaptor<String> sqlCaptor =
                    ArgumentCaptor.forClass(String.class);

            verify(jdbc).query(
                    sqlCaptor.capture(),
                    any(RowMapper.class),
                    any(Object[].class)
            );

            assertThat(sqlCaptor.getValue())
                    .contains("limit 1");
        }

        @Test
        void shouldClampLimitToMaximumHundred() {
            when(jdbc.query(
                    anyString(),
                    any(RowMapper.class),
                    any(Object[].class)
            )).thenReturn(List.of());

            controller.webhooks(
                    VALID_KEY,
                    null,
                    1000
            );

            ArgumentCaptor<String> sqlCaptor =
                    ArgumentCaptor.forClass(String.class);

            verify(jdbc).query(
                    sqlCaptor.capture(),
                    any(RowMapper.class),
                    any(Object[].class)
            );

            assertThat(sqlCaptor.getValue())
                    .contains("limit 100");
        }

        @Test
        void shouldMapWebhookRows() throws Exception {
            UUID webhookId = UUID.randomUUID();

            ResultSet rs = mock(ResultSet.class);

            when(rs.getObject("id"))
                    .thenReturn(webhookId);

            when(rs.getString("event_id"))
                    .thenReturn("payment.authorized:0:123");

            when(rs.getString("event_type"))
                    .thenReturn("payment.authorized");

            when(rs.getString("status"))
                    .thenReturn("PENDING");

            when(rs.getInt("attempts"))
                    .thenReturn(2);

            when(rs.getTimestamp("next_attempt_at"))
                    .thenReturn(Timestamp.from(
                            Instant.parse("2026-01-01T10:05:00Z")
                    ));

            when(rs.getTimestamp("created_at"))
                    .thenReturn(Timestamp.from(
                            Instant.parse("2026-01-01T10:00:00Z")
                    ));

            stubQueryWithRowMapper(rs);

            List<Map<String, Object>> result =
                    controller.webhooks(
                            VALID_KEY,
                            null,
                            20
                    );

            assertThat(result).hasSize(1);

            assertThat(result.get(0))
                    .containsEntry(
                            "id",
                            webhookId.toString()
                    )
                    .containsEntry(
                            "eventId",
                            "payment.authorized:0:123"
                    )
                    .containsEntry(
                            "eventType",
                            "payment.authorized"
                    )
                    .containsEntry("status", "PENDING")
                    .containsEntry("attempts", 2)
                    .containsEntry(
                            "nextAttemptAt",
                            "2026-01-01T10:05:00Z"
                    )
                    .containsEntry(
                            "createdAt",
                            "2026-01-01T10:00:00Z"
                    );
        }
    }

    @Nested
    @DisplayName("timeline")
    class Timeline {

        @Test
        void shouldBuildTimelineWithPendingEvent()
                throws Exception {

            UUID paymentId = UUID.randomUUID();

            stubPaymentForTimeline(paymentId);
            stubTimelineEvent(
                    "payment.authorization.requested",
                    null,
                    null
            );
            stubEmptyLedger();

            Map<String, Object> result =
                    controller.timeline(
                            VALID_KEY,
                            paymentId
                    );

            assertThat(result)
                    .containsKeys(
                            "payment",
                            "events",
                            "ledger"
                    );

            List<?> events =
                    (List<?>) result.get("events");

            assertThat(events).hasSize(1);

            @SuppressWarnings("unchecked")
            Map<String, Object> event =
                    (Map<String, Object>) events.get(0);

            assertThat(event)
                    .containsEntry(
                            "deliveryState",
                            "PENDING"
                    );
        }

        @Test
        void shouldBuildTimelineWithPublishedEvent()
                throws Exception {

            UUID paymentId = UUID.randomUUID();

            stubPaymentForTimeline(paymentId);
            stubTimelineEvent(
                    "payment.authorized",
                    Timestamp.from(
                            Instant.parse("2026-01-01T10:01:00Z")
                    ),
                    null
            );
            stubEmptyLedger();

            Map<String, Object> result =
                    controller.timeline(
                            VALID_KEY,
                            paymentId
                    );

            List<?> events =
                    (List<?>) result.get("events");

            @SuppressWarnings("unchecked")
            Map<String, Object> event =
                    (Map<String, Object>) events.get(0);

            assertThat(event)
                    .containsEntry(
                            "deliveryState",
                            "PUBLISHED"
                    );
        }

        @Test
        void shouldBuildTimelineWithFailedEvent()
                throws Exception {

            UUID paymentId = UUID.randomUUID();

            stubPaymentForTimeline(paymentId);
            stubTimelineEvent(
                    "payment.failed",
                    Timestamp.from(
                            Instant.parse("2026-01-01T10:01:00Z")
                    ),
                    Timestamp.from(
                            Instant.parse("2026-01-01T10:02:00Z")
                    )
            );
            stubEmptyLedger();

            Map<String, Object> result =
                    controller.timeline(
                            VALID_KEY,
                            paymentId
                    );

            List<?> events =
                    (List<?>) result.get("events");

            @SuppressWarnings("unchecked")
            Map<String, Object> event =
                    (Map<String, Object>) events.get(0);

            assertThat(event)
                    .containsEntry(
                            "deliveryState",
                            "FAILED"
                    );
        }
    }

    @Nested
    @DisplayName("incident snapshot")
    class IncidentSnapshot {

        @Test
        void shouldReturnIncidentSnapshot() {
            Map<String, Object> paymentStats =
                    Map.of(
                            "total", 100L,
                            "authorized", 80L,
                            "failed", 10L,
                            "pending", 10L
                    );

            Map<String, Object> outboxStats =
                    Map.of(
                            "pending", 2L,
                            "failed", 0L,
                            "published", 98L,
                            "max_attempts", 1
                    );

            when(jdbc.queryForMap(
                    contains("from payments")
            )).thenReturn(paymentStats);

            when(jdbc.queryForMap(
                    contains("from outbox_events")
            )).thenReturn(outboxStats);

            Map<String, Object> result =
                    controller.incidentSnapshot(VALID_KEY);

            assertThat(result.get("payments"))
                    .isEqualTo(paymentStats);

            assertThat(result.get("outbox"))
                    .isEqualTo(outboxStats);

            assertThat(result.get("provider"))
                    .isEqualTo(
                            Map.of(
                                    "name",
                                    "deterministic-mock-psp",
                                    "status",
                                    "UP",
                                    "mode",
                                    "deterministic-demo"
                            )
                    );

            assertThat(result.get("diagnosticNote"))
                    .isEqualTo(
                            "Demo provider deterministically declines approximately 10% of merchant references; production provider telemetry would be sourced from OTel/PSP health APIs."
                    );
        }
    }

    @SuppressWarnings({
            "unchecked",
            "rawtypes"
    })
    private void stubQueryWithExtractor(ResultSet rs)
            throws Exception {

        when(jdbc.query(
                anyString(),
                any(ResultSetExtractor.class),
                any()
        )).thenAnswer(invocation -> {

            ResultSetExtractor extractor =
                    invocation.getArgument(1);

            return extractor.extractData(rs);
        });
    }

    @SuppressWarnings({
            "unchecked",
            "rawtypes"
    })
    private void stubQueryWithRowMapper(ResultSet rs)
            throws Exception {

        when(jdbc.query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
        )).thenAnswer(invocation -> {

            RowMapper mapper =
                    invocation.getArgument(1);

            return List.of(
                    mapper.mapRow(rs, 0)
            );
        });
    }

    private void stubPaymentForTimeline(UUID paymentId)
            throws Exception {

        ResultSet rs = mock(ResultSet.class);

        when(rs.next()).thenReturn(true);
        when(rs.getObject("id"))
                .thenReturn(paymentId);
        when(rs.getString("merchant_reference"))
                .thenReturn("timeline-order");
        when(rs.getBigDecimal("amount"))
                .thenReturn(new BigDecimal("10.00"));
        when(rs.getString("currency"))
                .thenReturn("EUR");
        when(rs.getString("status"))
                .thenReturn("AUTHORIZED");
        when(rs.getString("provider_reference"))
                .thenReturn("provider-1");
        when(rs.getString("failure_reason"))
                .thenReturn(null);
        when(rs.getTimestamp("created_at"))
                .thenReturn(Timestamp.from(
                        Instant.parse("2026-01-01T10:00:00Z")
                ));
        when(rs.getTimestamp("updated_at"))
                .thenReturn(Timestamp.from(
                        Instant.parse("2026-01-01T10:01:00Z")
                ));
        when(rs.getLong("version"))
                .thenReturn(1L);

        when(jdbc.query(
                contains("from payments where id = ?"),
                any(ResultSetExtractor.class),
                eq(paymentId)
        )).thenAnswer(invocation -> {

            @SuppressWarnings("unchecked")
            ResultSetExtractor<Object> extractor =
                    invocation.getArgument(1);

            return extractor.extractData(rs);
        });
    }

    private void stubTimelineEvent(
            String eventType,
            Timestamp publishedAt,
            Timestamp failedAt
    ) throws Exception {

        ResultSet rs = mock(ResultSet.class);

        UUID eventId = UUID.randomUUID();

        when(rs.getObject("id"))
                .thenReturn(eventId);

        when(rs.getString("event_type"))
                .thenReturn(eventType);

        when(rs.getTimestamp("created_at"))
                .thenReturn(Timestamp.from(
                        Instant.parse("2026-01-01T10:00:00Z")
                ));

        /*
         * A production kód először a failed_at mezőt ellenőrzi.
         * Ha FAILED, akkor published_at már nem kerül kiolvasásra.
         */
        when(rs.getTimestamp("failed_at"))
                .thenReturn(failedAt);

        if (failedAt == null) {
            when(rs.getTimestamp("published_at"))
                    .thenReturn(publishedAt);
        }

        when(rs.getInt("attempts"))
                .thenReturn(1);

        when(rs.getString("last_error"))
                .thenReturn(
                        failedAt == null
                                ? null
                                : "delivery failed"
                );

        when(jdbc.query(
                contains("from outbox_events where aggregate_id = ?"),
                any(RowMapper.class),
                any()
        )).thenAnswer(invocation -> {

            @SuppressWarnings("unchecked")
            RowMapper<Map<String, Object>> mapper =
                    invocation.getArgument(1);

            return List.of(
                    mapper.mapRow(rs, 0)
            );
        });
    }

    private void stubEmptyLedger() {
        when(jdbc.query(
                contains("from ledger_entries where payment_id = ?"),
                any(RowMapper.class),
                any()
        )).thenReturn(List.of());
    }
}