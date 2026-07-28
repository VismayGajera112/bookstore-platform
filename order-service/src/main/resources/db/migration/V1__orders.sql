-- order_db. user_id and book_id are values copied from other services' databases, so no foreign key
-- can enforce them; only order_item -> orders stays a real relationship, because both live here.
CREATE TABLE orders (
    id                    BIGSERIAL      PRIMARY KEY,
    user_id               BIGINT         NOT NULL,
    username              VARCHAR(50)    NOT NULL,
    status                VARCHAR(30)    NOT NULL,
    total_amount          NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    stock_reserved        BOOLEAN        NOT NULL DEFAULT false,
    stock_release_pending BOOLEAN        NOT NULL DEFAULT false,
    status_reason         VARCHAR(500),
    payment_id            BIGINT,
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_orders_status CHECK (status IN
        ('PENDING', 'AWAITING_PAYMENT', 'PAID', 'SHIPPED', 'CANCELLED'))
);

-- "My orders" is the most frequent query and always filters on user_id; newest first, hence the
-- descending id in the same index so the sort needs no extra step.
CREATE INDEX idx_orders_user_id_created ON orders (user_id, id DESC);

-- Admin dashboards filter by status, and the compensation sweeper scans only the few rows still owing
-- a release — a partial index keeps that scan proportional to the backlog, not to the table.
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_release_pending ON orders (id) WHERE stock_release_pending;

CREATE TABLE order_item (
    id         BIGSERIAL      PRIMARY KEY,
    order_id   BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    book_id    BIGINT         NOT NULL,
    book_title VARCHAR(255)   NOT NULL,
    quantity   INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0)
);

-- Items are always read for a known order, and the FK column needs its own index in PostgreSQL.
CREATE INDEX idx_order_item_order_id ON order_item (order_id);
