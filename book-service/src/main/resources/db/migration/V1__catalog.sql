-- Trigram support so that infix keyword search ("%clean%") can still use an index.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE author (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Case-insensitive uniqueness: "Eric Evans" and "eric evans" are the same author.
CREATE UNIQUE INDEX uk_author_name_lower ON author (lower(name));

CREATE TABLE book (
    id         BIGSERIAL      PRIMARY KEY,
    title      VARCHAR(255)   NOT NULL,
    author_id  BIGINT         NOT NULL REFERENCES author (id) ON DELETE RESTRICT,
    isbn       VARCHAR(13)    UNIQUE,
    price      NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    stock      INTEGER        NOT NULL CHECK (stock >= 0),
    cover_url  VARCHAR(512),
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- Every join and filter from a book back to its author uses this column. PostgreSQL does not
-- index foreign keys automatically, and without it "all books by author X" is a sequential scan.
CREATE INDEX idx_book_author_id ON book (author_id);

-- B-tree on title serves equality lookups, prefix matches ("Clean%") and ORDER BY title.
CREATE INDEX idx_book_title ON book (title);

-- B-tree cannot help a leading-wildcard LIKE, so trigram GIN indexes cover the keyword search.
CREATE INDEX idx_book_title_trgm ON book USING gin (lower(title) gin_trgm_ops);
CREATE INDEX idx_author_name_trgm ON author USING gin (lower(name) gin_trgm_ops);

-- Stock handed out to an order. order_id is owned by order-service in a different database, so it is
-- stored as a plain value with no foreign key: the unique constraint is what makes a retried reserve
-- call idempotent, and the stored lines are what a compensating release replays in reverse.
CREATE TABLE stock_reservation (
    id          BIGSERIAL   PRIMARY KEY,
    order_id    BIGINT      NOT NULL UNIQUE,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    released_at TIMESTAMPTZ,
    CONSTRAINT ck_stock_reservation_status CHECK (status IN ('RESERVED', 'RELEASED'))
);

CREATE TABLE stock_reservation_item (
    reservation_id BIGINT  NOT NULL REFERENCES stock_reservation (id) ON DELETE CASCADE,
    book_id        BIGINT  NOT NULL REFERENCES book (id) ON DELETE RESTRICT,
    quantity       INTEGER NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_stock_reservation_item_reservation ON stock_reservation_item (reservation_id);
