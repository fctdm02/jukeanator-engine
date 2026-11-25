-- Per-location patron accounts: credits, song-play history, playlists, credit spends and Add
-- Funds purchases now belong to a (user, location) association -- user_location_account --
-- instead of directly to user_account. JukeANator has no central song library: every location
-- plays its own licensed copies, so a credit balance, a play history or a playlist is only
-- meaningful at the location it was built at. An Add Funds purchase is likewise a purchase for
-- one location, recorded in full in that location's financial ledger (location_transaction,
-- transaction_type 'MOBILE_WEB') like a local credit-card swipe.

-- ─── 1. Clear test patron data ───────────────────────────────────────────────────────────────
-- Production held only a few test accounts when this migration was written; their balances were
-- refunded/zeroed outside the app. User accounts themselves are kept. Cash/card ledger rows and
-- jukebox-split periods are real kiosk money and are kept.
DELETE FROM user_song_credit_usage;
DELETE FROM user_add_funds_transaction;
DELETE FROM user_playlist_song;
DELETE FROM user_playlist;
DELETE FROM user_song_play_history;
DELETE FROM location_mobile_credit_usage;

-- ─── 2. The (user, location) association ─────────────────────────────────────────────────────
-- location_id is deliberately left without an FK to location (same convention as the song id
-- columns in V1): standalone and slave instances have no location row for themselves.
CREATE TABLE user_location_account (
    persistent_identity INT PRIMARY KEY,
    user_id              INT NOT NULL,
    location_id            INT NOT NULL,
    num_credits              INT NOT NULL DEFAULT 0,
    version                    INT NOT NULL DEFAULT 1,
    date_added                   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated                   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_location_account_user_account FOREIGN KEY (user_id) REFERENCES user_account (persistent_identity),
    CONSTRAINT uq_user_location_account UNIQUE (user_id, location_id)
) ENGINE=InnoDB;

-- ─── 3. No more global balance ───────────────────────────────────────────────────────────────
ALTER TABLE user_account DROP COLUMN num_credits;

-- ─── 4. Re-parent the per-user tables onto the location account (all empty after step 1) ────
ALTER TABLE user_playlist DROP FOREIGN KEY fk_user_playlist_user_account;
ALTER TABLE user_playlist DROP COLUMN user_id;
ALTER TABLE user_playlist DROP COLUMN owner;
ALTER TABLE user_playlist ADD COLUMN user_location_account_id INT NOT NULL AFTER persistent_identity;
ALTER TABLE user_playlist ADD CONSTRAINT fk_user_playlist_user_location_account
    FOREIGN KEY (user_location_account_id) REFERENCES user_location_account (persistent_identity);
ALTER TABLE user_playlist ADD CONSTRAINT uq_user_playlist_name UNIQUE (user_location_account_id, name);

ALTER TABLE user_song_play_history DROP FOREIGN KEY fk_song_play_history_user_account;
ALTER TABLE user_song_play_history DROP COLUMN user_id;
ALTER TABLE user_song_play_history ADD COLUMN user_location_account_id INT NOT NULL FIRST;
ALTER TABLE user_song_play_history ADD CONSTRAINT fk_song_play_history_user_location_account
    FOREIGN KEY (user_location_account_id) REFERENCES user_location_account (persistent_identity);
ALTER TABLE user_song_play_history ADD PRIMARY KEY (user_location_account_id, play_order);

ALTER TABLE user_song_credit_usage DROP FOREIGN KEY fk_user_song_credit_usage_user_account;
ALTER TABLE user_song_credit_usage DROP COLUMN user_id;
ALTER TABLE user_song_credit_usage ADD COLUMN user_location_account_id INT NOT NULL AFTER persistent_identity;
ALTER TABLE user_song_credit_usage ADD CONSTRAINT fk_user_song_credit_usage_user_location_account
    FOREIGN KEY (user_location_account_id) REFERENCES user_location_account (persistent_identity);

ALTER TABLE user_add_funds_transaction DROP FOREIGN KEY fk_user_add_funds_transaction_user_account;
ALTER TABLE user_add_funds_transaction DROP COLUMN user_id;
ALTER TABLE user_add_funds_transaction ADD COLUMN user_location_account_id INT NOT NULL AFTER persistent_identity;
ALTER TABLE user_add_funds_transaction ADD COLUMN location_id INT NOT NULL AFTER user_location_account_id;
ALTER TABLE user_add_funds_transaction ADD CONSTRAINT fk_user_add_funds_transaction_user_location_account
    FOREIGN KEY (user_location_account_id) REFERENCES user_location_account (persistent_identity);
CREATE INDEX ix_user_add_funds_transaction_location_timestamp ON user_add_funds_transaction (location_id, timestamp);

-- ─── 5. Mobile/web purchases in the location financial ledger ────────────────────────────────
-- amount_dollars (whole dollars) becomes amount (DECIMAL) so a credit package need not be priced
-- in whole dollars. Cash and card rows keep posting 1.00 per bill/pulse. The new MOBILE_WEB
-- discriminator value carries the purchase details in the nullable columns below; on a slave
-- these rows are master's mirror (source_transaction_id = master's persistent_identity).
ALTER TABLE location_transaction ADD COLUMN amount DECIMAL(12,2) NULL AFTER amount_dollars;
UPDATE location_transaction SET amount = amount_dollars;
ALTER TABLE location_transaction MODIFY COLUMN amount DECIMAL(12,2) NOT NULL;
ALTER TABLE location_transaction DROP COLUMN amount_dollars;
ALTER TABLE location_transaction ADD COLUMN user_email VARCHAR(255) NULL;
ALTER TABLE location_transaction ADD COLUMN payment_reference VARCHAR(255) NULL;
ALTER TABLE location_transaction ADD COLUMN package_code VARCHAR(64) NULL;
ALTER TABLE location_transaction ADD COLUMN credits_awarded INT NULL;
ALTER TABLE location_transaction ADD COLUMN bonus_credits INT NULL;
CREATE INDEX ix_location_transaction_location_timestamp ON location_transaction (location_id, timestamp);

-- ─── 6. Per-location credit packages ─────────────────────────────────────────────────────────
-- Pushed up by each slave alongside its pricing config (see PricingConfigDto.creditPackages).
CREATE TABLE location_credit_package (
    persistent_identity INT PRIMARY KEY,
    location_id          INT NOT NULL,
    package_code           VARCHAR(64) NOT NULL,
    price_usd                DECIMAL(12,2) NOT NULL,
    credits                    INT NOT NULL,
    bonus_credits                INT NOT NULL,
    badge                          VARCHAR(64) NULL,
    display_order                    INT NOT NULL,
    version                            INT NOT NULL DEFAULT 1,
    date_added                           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated                           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_location_credit_package_location FOREIGN KEY (location_id) REFERENCES location (id)
        ON UPDATE CASCADE,
    CONSTRAINT uq_location_credit_package UNIQUE (location_id, package_code)
) ENGINE=InnoDB;
