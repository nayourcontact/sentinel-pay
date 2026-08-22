package com.acme.payments.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LedgerRepository extends JpaRepository<LedgerEntryEntity, UUID> {

    boolean existsByPaymentIdAndEntryType(UUID paymentId, String entryType);
}