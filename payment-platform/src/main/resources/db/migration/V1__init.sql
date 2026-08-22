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

create table ledger_entries (
    id uuid primary key,
    payment_id uuid not null references payments(id),
    entry_type varchar(100) not null,
    amount numeric(19,4) not null,
    currency varchar(3) not null,
    created_at timestamptz not null,
    constraint uk_ledger_payment_type unique(payment_id, entry_type)
);

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
