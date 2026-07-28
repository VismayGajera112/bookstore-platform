-- user_db owns identity for the whole platform. No other service's schema references these rows.
CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

-- Login is always a username lookup, and usernames must be unique regardless of case.
-- A unique index on lower(username) enforces the rule and serves the login query in one structure.
CREATE UNIQUE INDEX uk_users_username_lower ON users (lower(username));
CREATE UNIQUE INDEX uk_users_email_lower ON users (lower(email));
