package com.acme.payments.adapter.in.web;

import com.acme.payments.application.PaymentApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ApiExceptionHandler")
class ApiExceptionHandlerTest {

    private ApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApiExceptionHandler();
    }

    @Test
    void shouldConvertPaymentNotFoundExceptionTo404ProblemDetail() {
        PaymentApplicationService.PaymentNotFoundException ex =
                mock(
                        PaymentApplicationService
                                .PaymentNotFoundException.class
                );

        when(ex.getMessage())
                .thenReturn("payment not found: 123");

        ProblemDetail result =
                handler.notFound(ex);

        assertThat(result.getStatus())
                .isEqualTo(
                        HttpStatus.NOT_FOUND.value()
                );

        assertThat(result.getTitle())
                .isEqualTo("Payment not found");

        assertThat(result.getDetail())
                .isEqualTo("payment not found: 123");
    }

    @Test
    void shouldConvertValidationExceptionTo400ProblemDetail() {
        MethodArgumentNotValidException ex =
                mock(
                        MethodArgumentNotValidException.class
                );

        ProblemDetail result =
                handler.validation(ex);

        assertThat(result.getStatus())
                .isEqualTo(
                        HttpStatus.BAD_REQUEST.value()
                );

        assertThat(result.getTitle())
                .isEqualTo("Invalid request");

        assertThat(result.getDetail())
                .isEqualTo(
                        "request validation failed"
                );
    }

    @Test
    void shouldConvertHandlerMethodValidationExceptionTo400ProblemDetail() {
        HandlerMethodValidationException ex =
                mock(HandlerMethodValidationException.class);

        ProblemDetail result =
                handler.validation(ex);

        assertThat(result.getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());

        assertThat(result.getTitle())
                .isEqualTo("Invalid request");

        assertThat(result.getDetail())
                .isEqualTo("request validation failed");
    }
}