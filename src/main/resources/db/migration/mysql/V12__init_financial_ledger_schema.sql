-- New tables for the Financial Ledger / jukebox-split feature. Neither is owned by another
-- aggregate (unlike credit_transactions, which belongs to a user) -- both are freestanding,
-- sharing the persistent_identity_seq emulation table with every other AbstractPersistentEntity
-- subclass (see AbstractPersistentEntity.PERSISTENT_IDENTITY_SEQUENCE), and keeping the default
-- persistent_identity PK column name (only location/song_library rename it to "id").

CREATE TABLE jukebox_split_period (
    persistent_identity         INT PRIMARY KEY,
    version                     INT NOT NULL DEFAULT 1,
    start_date                  TIMESTAMP NOT NULL,
    end_date                    TIMESTAMP NULL,
    split_percentage_to_owner   INT NULL,
    cash_total                  DECIMAL(12,2) NULL,
    card_total                  DECIMAL(12,2) NULL,
    mobile_total                DECIMAL(12,2) NULL,
    total_earned                DECIMAL(12,2) NULL,
    amount_due_owner            DECIMAL(12,2) NULL,
    amount_due_operator         DECIMAL(12,2) NULL
) ENGINE=InnoDB;

-- Append-only local (bill-acceptor / credit-card-reader) credit-award history, analogous to
-- credit_transactions for mobile/web credits.
CREATE TABLE local_credit_transactions (
    persistent_identity INT PRIMARY KEY,
    version              INT NOT NULL DEFAULT 1,
    source               VARCHAR(255) NOT NULL,
    amount_dollars       INT NOT NULL,
    timestamp            TIMESTAMP NOT NULL,
    location_id          INT NULL
) ENGINE=InnoDB;
