CREATE TABLE processed_events (
    event_id     VARCHAR(64) PRIMARY KEY,
    order_id     BIGINT      NOT NULL,
    processed_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_processed_events_order_id ON processed_events (order_id);
