package com.acme.payments.adapter.in.web;

import com.acme.payments.application.PaymentApplicationService;
import com.acme.payments.domain.Payment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentApplicationService service;
    public PaymentController(PaymentApplicationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<PaymentView> create(@Valid @RequestBody CreatePaymentRequest request,
                                              @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {
        var payment = service.create(new PaymentApplicationService.CreatePayment(idempotencyKey, request.amount(), request.currency()));
        return ResponseEntity.created(URI.create("/api/payments/" + payment.id())).body(PaymentView.from(payment));
    }

    @GetMapping("/{id}")
    public PaymentView get(@PathVariable UUID id) { return PaymentView.from(service.get(id)); }

    public record CreatePaymentRequest(
            @NotBlank @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,4})?$") String amount,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency) {}

    public record PaymentView(UUID id, String merchantReference, String amount, String currency, String status,
                              String providerReference, String failureReason, String createdAt, String updatedAt) {
        static PaymentView from(Payment p) {
            return new PaymentView(p.id(), p.merchantReference(), p.money().amount().toPlainString(),
                    p.money().currency().getCurrencyCode(), p.status().name(), p.providerReference(), p.failureReason(),
                    p.createdAt().toString(), p.updatedAt().toString());
        }
    }
}
