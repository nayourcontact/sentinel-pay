-- Nullable metadata: existing pending/published rows continue to work unchanged.
-- Persist identifiers only, never baggage, access tokens or full trace headers.
ALTER TABLE outbox_events ADD COLUMN origin_trace_id varchar(32);
ALTER TABLE outbox_events ADD COLUMN origin_span_id varchar(16);
ALTER TABLE outbox_events ADD COLUMN origin_trace_flags varchar(2);
