-- Fresh database baseline equivalent to the deployed V1 + V2 + V3 schema.
-- Preserve historic V1/V2/V3 files and their checksums for existing databases.
-- Flyway selects B3 only for newly initialized databases.

create table payments (
    id uuid primary key,
    merchant_reference varchar(255) not null unique,
    amount numeric(19,4) not null,
    currency varchar(3) not null,
    status varchar(40) not null,
    provider_reference varchar(255),
    failure_reason varchar(500),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
);

create index idx_payments_status on payments(status);

create table outbox_events (
    id uuid primary key,
    aggregate_type varchar(100) not null,
    aggregate_id uuid not null,
    event_type varchar(150) not null,
    payload text not null,
    created_at timestamptz not null,
    published_at timestamptz,
    failed_at timestamptz,
    last_error text,
    attempts integer not null default 0
);
create index idx_outbox_unpublished on outbox_events(created_at) where published_at is null;

create table webhook_deliveries (
    id uuid primary key,
    event_id varchar(255) not null,
    event_type varchar(150) not null,
    payload text not null,
    status varchar(40) not null,
    attempts integer not null default 0,
    next_attempt_at timestamptz not null,
    created_at timestamptz not null,
    constraint uk_webhook_event unique(event_id)
);

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

-- Nullable metadata: existing pending/published rows continue to work unchanged.
-- Persist identifiers only, never baggage, access tokens or full trace headers.
ALTER TABLE outbox_events ADD COLUMN origin_trace_id varchar(32);
ALTER TABLE outbox_events ADD COLUMN origin_span_id varchar(16);
ALTER TABLE outbox_events ADD COLUMN origin_trace_flags varchar(2);
