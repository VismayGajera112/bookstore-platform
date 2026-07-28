-- payment_db. order_id and user_id belong to other services, so they are stored as values.
CREATE TABLE payment (
    id             BIGSERIAL      PRIMARY KEY,
    -- One payment per order, enforced by the database rather than by application checks: this is what
    -- makes a retried or double-clicked payment request impossible to charge twice.
    order_id       BIGINT         NOT NULL UNIQUE,
    user_id        BIGINT         NOT NULL,
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    status         VARCHAR(20)    NOT NULL,
    card_last4     VARCHAR(4),
    failure_reason VARCHAR(500),
    order_notified BOOLEAN        NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_payment_status CHECK (status IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX idx_payment_user_id ON payment (user_id);

-- The redelivery sweeper only ever scans undelivered verdicts, so the index covers just those rows.
CREATE INDEX idx_payment_unnotified ON payment (id) WHERE NOT order_notified;
