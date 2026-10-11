package com.acme.payments.adapter.out.persistence;

import com.acme.payments.application.PaymentRepository;
import com.acme.payments.domain.Money;
import com.acme.payments.domain.Payment;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaPaymentRepository implements PaymentRepository {
    private final SpringDataPaymentRepository delegate;
    private final JdbcTemplate jdbcTemplate;

    JpaPaymentRepository(SpringDataPaymentRepository delegate, JdbcTemplate jdbcTemplate) {
        this.delegate = delegate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override public void lockMerchantReference(String merchantReference) {
        // Transaction-scoped PostgreSQL advisory lock; released automatically at commit/rollback.
        // Lock before checking existence to prevent concurrent check-then-insert races.
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                ps -> ps.setString(1, merchantReference),
                rs -> { /* lock acquired when the query returns */ });
    }

    @Override public Payment save(Payment payment) {
        PaymentEntity entity = delegate.findById(payment.id()).orElseGet(PaymentEntity::new);
        entity.id = payment.id();
        entity.merchantReference = payment.merchantReference();
        entity.amount = payment.money().amount();
        entity.currency = payment.money().currency().getCurrencyCode();
        entity.status = payment.status();
        entity.providerReference = payment.providerReference();
        entity.failureReason = payment.failureReason();
        entity.createdAt = payment.createdAt();
        entity.updatedAt = payment.updatedAt();
        return toDomain(delegate.save(entity));
    }

    @Override public Optional<Payment> findById(UUID id) { return delegate.findById(id).map(this::toDomain); }
    @Override public Optional<Payment> findByMerchantReference(String ref) { return delegate.findByMerchantReference(ref).map(this::toDomain); }

    private Payment toDomain(PaymentEntity e) {
        return Payment.rehydrate(e.id, e.merchantReference, new Money(e.amount, Currency.getInstance(e.currency)), e.status,
                e.providerReference, e.failureReason, e.createdAt, e.updatedAt, e.version);
    }
}
