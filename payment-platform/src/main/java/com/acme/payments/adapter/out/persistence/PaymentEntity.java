package com.acme.payments.adapter.out.persistence;

import com.acme.payments.domain.PaymentStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
class PaymentEntity {
    @Id UUID id;
    @Column(nullable = false, unique = true) String merchantReference;
    @Column(nullable = false, precision = 19, scale = 4) BigDecimal amount;
    @Column(nullable = false, length = 3) String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false) PaymentStatus status;
    String providerReference;
    String failureReason;
    @Column(nullable = false) Instant createdAt;
    @Column(nullable = false) Instant updatedAt;
    @Version long version;
}
