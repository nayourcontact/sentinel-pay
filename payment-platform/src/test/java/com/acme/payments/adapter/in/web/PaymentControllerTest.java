package com.acme.payments.adapter.in.web;

import com.acme.payments.application.PaymentApplicationService;
import com.acme.payments.domain.Money;
import com.acme.payments.domain.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.context.annotation.Import;

@WebMvcTest(PaymentController.class)
@Import(ApiExceptionHandler.class)
@DisplayName("PaymentController")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentApplicationService service;

    @Nested
    @DisplayName("POST /api/payments")
    class CreatePayment {

        @Test
        void shouldCreatePayment() throws Exception {
            Payment payment = Payment.create(
                    "order-2026-001",
                    Money.of("149.90", "EUR")
            );

            payment.requestAuthorization();

            when(service.create(
                    any(PaymentApplicationService.CreatePayment.class)
            )).thenReturn(payment);

            mockMvc.perform(
                            post("/api/payments")
                                    .header(
                                            "Idempotency-Key",
                                            "order-2026-001"
                                    )
                                    .contentType(APPLICATION_JSON)
                                    .content("""
                                            {
                                              "amount": "149.90",
                                              "currency": "EUR"
                                            }
                                            """)
                    )
                    .andExpect(status().isCreated())
                    .andExpect(
                            header().string(
                                    "Location",
                                    "/api/payments/" + payment.id()
                            )
                    )
                    .andExpect(
                            content()
                                    .contentTypeCompatibleWith(
                                            APPLICATION_JSON
                                    )
                    )
                    .andExpect(
                            jsonPath("$.id")
                                    .value(payment.id().toString())
                    )
                    .andExpect(
                            jsonPath("$.merchantReference")
                                    .value("order-2026-001")
                    )
                    .andExpect(
                            jsonPath("$.amount")
                                    .value("149.90")
                    )
                    .andExpect(
                            jsonPath("$.currency")
                                    .value("EUR")
                    )
                    .andExpect(
                            jsonPath("$.status")
                                    .value("AUTHORIZATION_PENDING")
                    )
                    .andExpect(
                            jsonPath("$.providerReference")
                                    .isEmpty()
                    )
                    .andExpect(
                            jsonPath("$.failureReason")
                                    .isEmpty()
                    )
                    .andExpect(
                            jsonPath("$.createdAt")
                                    .isNotEmpty()
                    )
                    .andExpect(
                            jsonPath("$.updatedAt")
                                    .isNotEmpty()
                    );

            ArgumentCaptor<PaymentApplicationService.CreatePayment>
                    captor =
                    ArgumentCaptor.forClass(
                            PaymentApplicationService.CreatePayment.class
                    );

            verify(service).create(captor.capture());

            PaymentApplicationService.CreatePayment command =
                    captor.getValue();

            assertThat(command.merchantReference())
                    .isEqualTo("order-2026-001");

            assertThat(command.amount())
                    .isEqualTo("149.90");

            assertThat(command.currency())
                    .isEqualTo("EUR");

            verifyNoMoreInteractions(service);
        }

        @Test
        void shouldRejectMissingIdempotencyKey() throws Exception {
            mockMvc.perform(
                            post("/api/payments")
                                    .contentType(APPLICATION_JSON)
                                    .content("""
                                            {
                                              "amount": "149.90",
                                              "currency": "EUR"
                                            }
                                            """)
                    )
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(service);
        }

        @Test
        void shouldRejectBlankAmount() throws Exception {
            mockMvc.perform(
                            post("/api/payments")
                                    .header(
                                            "Idempotency-Key",
                                            "order-1"
                                    )
                                    .contentType(APPLICATION_JSON)
                                    .content("""
                                            {
                                              "amount": "",
                                              "currency": "EUR"
                                            }
                                            """)
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(
                            jsonPath("$.title")
                                    .value("Invalid request")
                    )
                    .andExpect(
                            jsonPath("$.detail")
                                    .value(
                                            "request validation failed"
                                    )
                    );

            verifyNoInteractions(service);
        }

        @Test
        void shouldRejectNegativeAmount() throws Exception {
            mockMvc.perform(
                            post("/api/payments")
                                    .header(
                                            "Idempotency-Key",
                                            "order-1"
                                    )
                                    .contentType(APPLICATION_JSON)
                                    .content("""
                                            {
                                              "amount": "-10.00",
                                              "currency": "EUR"
                                            }
                                            """)
                    )
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(service);
        }

        @Test
        void shouldRejectAmountWithMoreThanFourDecimalPlaces()
                throws Exception {

            mockMvc.perform(
                            post("/api/payments")
                                    .header(
                                            "Idempotency-Key",
                                            "order-1"
                                    )
                                    .contentType(APPLICATION_JSON)
                                    .content("""
                                            {
                                              "amount": "10.12345",
                                              "currency": "EUR"
                                            }
                                            """)
                    )
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(service);
        }

        @Test
        void shouldRejectLowercaseCurrency() throws Exception {
            mockMvc.perform(
                            post("/api/payments")
                                    .header(
                                            "Idempotency-Key",
                                            "order-1"
                                    )
                                    .contentType(APPLICATION_JSON)
                                    .content("""
                                            {
                                              "amount": "10.00",
                                              "currency": "eur"
                                            }
                                            """)
                    )
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(service);
        }

        @Test
        void shouldRejectCurrencyLongerThanThreeCharacters()
                throws Exception {

            mockMvc.perform(
                            post("/api/payments")
                                    .header(
                                            "Idempotency-Key",
                                            "order-1"
                                    )
                                    .contentType(APPLICATION_JSON)
                                    .content("""
                                            {
                                              "amount": "10.00",
                                              "currency": "EURO"
                                            }
                                            """)
                    )
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(service);
        }

        @Test
        void shouldAcceptAmountWithThreeDecimalPlaces()
                throws Exception {

            Payment payment = Payment.create(
                    "order-three-decimals",
                    Money.of("10.123", "BHD")
            );

            when(service.create(
                    any(PaymentApplicationService.CreatePayment.class)
            )).thenReturn(payment);

            mockMvc.perform(
                            post("/api/payments")
                                    .header(
                                            "Idempotency-Key",
                                            "order-three-decimals"
                                    )
                                    .contentType(APPLICATION_JSON)
                                    .content("""
                                    {
                                      "amount": "10.123",
                                      "currency": "BHD"
                                    }
                                    """)
                    )
                    .andExpect(status().isCreated());

            verify(service).create(any());
        }
    }

    @Nested
    @DisplayName("GET /api/payments/{id}")
    class GetPayment {

        @Test
        void shouldReturnPayment() throws Exception {
            Payment payment = Payment.create(
                    "order-get-1",
                    Money.of("49.99", "EUR")
            );

            payment.requestAuthorization();
            payment.markAuthorized("provider-123");

            when(service.get(payment.id()))
                    .thenReturn(payment);

            mockMvc.perform(
                            get(
                                    "/api/payments/{id}",
                                    payment.id()
                            )
                    )
                    .andExpect(status().isOk())
                    .andExpect(
                            content()
                                    .contentTypeCompatibleWith(
                                            APPLICATION_JSON
                                    )
                    )
                    .andExpect(
                            jsonPath("$.id")
                                    .value(payment.id().toString())
                    )
                    .andExpect(
                            jsonPath("$.merchantReference")
                                    .value("order-get-1")
                    )
                    .andExpect(
                            jsonPath("$.amount")
                                    .value("49.99")
                    )
                    .andExpect(
                            jsonPath("$.currency")
                                    .value("EUR")
                    )
                    .andExpect(
                            jsonPath("$.status")
                                    .value("AUTHORIZED")
                    )
                    .andExpect(
                            jsonPath("$.providerReference")
                                    .value("provider-123")
                    )
                    .andExpect(
                            jsonPath("$.failureReason")
                                    .isEmpty()
                    );

            verify(service).get(payment.id());
            verifyNoMoreInteractions(service);
        }

        @Test
        void shouldReturn404WhenPaymentDoesNotExist()
                throws Exception {

            UUID paymentId = UUID.randomUUID();

            PaymentApplicationService.PaymentNotFoundException ex =
                    mock(
                            PaymentApplicationService
                                    .PaymentNotFoundException.class
                    );

            when(ex.getMessage())
                    .thenReturn(
                            "payment not found: " + paymentId
                    );

            when(service.get(paymentId))
                    .thenThrow(ex);

            mockMvc.perform(
                            get(
                                    "/api/payments/{id}",
                                    paymentId
                            )
                    )
                    .andExpect(status().isNotFound())
                    .andExpect(
                            jsonPath("$.title")
                                    .value("Payment not found")
                    )
                    .andExpect(
                            jsonPath("$.detail")
                                    .value(
                                            "payment not found: "
                                                    + paymentId
                                    )
                    );

            verify(service).get(paymentId);
        }

        @Test
        void shouldRejectMalformedPaymentId() throws Exception {
            mockMvc.perform(
                            get(
                                    "/api/payments/{id}",
                                    "not-a-uuid"
                            )
                    )
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(service);
        }
    }
}