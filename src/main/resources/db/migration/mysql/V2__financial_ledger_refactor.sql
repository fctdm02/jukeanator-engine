-- Financial ledger refactor -- see docs/financial-ledger-refactor-and-sync.md.
--
-- Splits what used to be one ambiguous "credit transaction" concept into two genuinely different
-- entities: a user obtaining song credits with real money (user_add_funds_transaction, new) vs. a
-- user spending already-owned song credits to queue a song at a location (renamed from
-- mobile_transactions to user_song_credit_usage). Also merges the two local (bill-acceptor /
-- credit-card-reader) transaction tables into one location_transaction table, discriminated by
-- transaction_type, so a central DBA can see both streams together and filter by location.
--
-- No data-migrating ALTER/INSERT SELECT statements -- this project has not been deployed to
-- production (same rationale as the V1 baseline consolidation), so old rows are not preserved.

-- ── user_song_credit_usage (renamed from mobile_transactions) ──────────────────────────────
DROP TABLE mobile_transactions;

CREATE TABLE user_song_credit_usage (
    persistent_identity INT PRIMARY KEY,
    user_id              INT,
    location_id            INT,
    amount                   INT NOT NULL,
    type                       VARCHAR(255) NOT NULL,
    timestamp                   TIMESTAMP NOT NULL,
    song_album_id                 INT,
    song_id                         INT,
    resulting_balance                 INT NOT NULL,
    version                             INT NOT NULL DEFAULT 1,
    date_added                           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated                           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_song_credit_usage_user FOREIGN KEY (user_id) REFERENCES users (persistent_identity)
) ENGINE=InnoDB;

-- ── user_add_funds_transaction (new) ────────────────────────────────────────────────────────
-- A user spending real money (via PaymentGateway, e.g. Braintree) to obtain song credits usable
-- at any location -- never location-attributed, unlike user_song_credit_usage above.
CREATE TABLE user_add_funds_transaction (
    persistent_identity INT PRIMARY KEY,
    user_id              INT,
    package_id             VARCHAR(64),
    credits_awarded          INT NOT NULL,
    bonus_credits              INT NOT NULL,
    amount_usd                   DECIMAL(12,2) NOT NULL,
    payment_source                 VARCHAR(64),
    payment_transaction_id           VARCHAR(255),
    timestamp                          TIMESTAMP NOT NULL,
    resulting_balance                    INT NOT NULL,
    version                                INT NOT NULL DEFAULT 1,
    date_added                               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated                               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_add_funds_transaction_user FOREIGN KEY (user_id) REFERENCES users (persistent_identity)
) ENGINE=InnoDB;

-- ── location_transaction (merges local_cash_transactions + local_credit_transactions) ──────
-- transaction_type discriminates CASH | CREDIT_CARD (see AbstractLocationTransactionEntity's
-- single-table JPA inheritance). source_transaction_id is the slave-to-master mirror's idempotency
-- key: NULL for every transaction recorded directly on the instance that owns it (the normal case
-- on a slave/standalone instance); only master-received mirrored rows ever populate it, carrying
-- the slave's own local persistent_identity for that transaction. The composite unique key only
-- meaningfully constrains mirrored rows -- MySQL treats any row containing a NULL indexed column
-- as distinct from every other row in a unique index, so genuinely local transactions never
-- collide with each other.
DROP TABLE local_cash_transactions;
DROP TABLE local_credit_transactions;

CREATE TABLE location_transaction (
    persistent_identity INT PRIMARY KEY,
    transaction_type      VARCHAR(20) NOT NULL,
    version                 INT NOT NULL DEFAULT 1,
    amount_dollars            INT NOT NULL,
    timestamp                   TIMESTAMP NOT NULL,
    location_id                   INT NULL,
    source_transaction_id           INT NULL,
    date_added                        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated                        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_location_transaction_source UNIQUE (location_id, source_transaction_id)
) ENGINE=InnoDB;

CREATE INDEX ix_location_transaction_location ON location_transaction (location_id);
