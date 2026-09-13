-- Cash and credit-card-reader credits were originally kept in one local_credit_transactions
-- table behind a `source` discriminator column (see V12__init_financial_ledger_schema.sql) --
-- confusing next to the similarly-named mobile_transactions table (V13), and easy to misread as
-- "the local table" rather than "the local credit-card table" specifically. Split into two
-- tables instead, one per revenue stream, so neither name is ambiguous on its own.
--
-- Clean slate, no production data yet (same ground rule as V4/V7/V8/V9) -- existing rows (if any,
-- from local development/testing only) are simply dropped rather than migrated by source value.

CREATE TABLE local_cash_transactions (
    persistent_identity INT PRIMARY KEY,
    version              INT NOT NULL DEFAULT 1,
    amount_dollars       INT NOT NULL,
    timestamp            TIMESTAMP NOT NULL,
    location_id          INT NULL
) ENGINE=InnoDB;

DELETE FROM local_credit_transactions;
ALTER TABLE local_credit_transactions DROP COLUMN source;
