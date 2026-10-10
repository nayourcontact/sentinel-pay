CREATE TABLE ledger_entries (
                                id UUID PRIMARY KEY,
                                payment_id UUID NOT NULL,
                                entry_type VARCHAR(64) NOT NULL,
                                amount NUMERIC(19, 2) NOT NULL,
                                currency VARCHAR(3) NOT NULL,
                                created_at TIMESTAMPTZ NOT NULL,

                                CONSTRAINT uq_ledger_entries_payment_type
                                    UNIQUE (payment_id, entry_type)
);

CREATE INDEX idx_ledger_entries_payment_id
    ON ledger_entries(payment_id);

CREATE INDEX idx_ledger_entries_created_at
    ON ledger_entries(created_at);