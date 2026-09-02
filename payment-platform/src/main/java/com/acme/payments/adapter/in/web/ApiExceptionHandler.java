package com.acme.payments.adapter.in.web;

import com.acme.payments.application.PaymentApplicationService.PaymentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail notFound(PaymentNotFoundException ex) {
        var p = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        p.setTitle("Payment not found");
        return p;
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class
    })
    ProblemDetail validation(Exception ex) {
        var p = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "request validation failed"
        );
        p.setTitle("Invalid request");
        return p;
    }
}