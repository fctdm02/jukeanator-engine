-- ─────────────────────────────────────────────────────────────────────────────────────────────
-- Brings an EXISTING jukeanator-engine database up to date with the license server tables that
-- were added to V1__init_schema.sql, without dropping the database.
--
-- Run it once per database, connected to that database's schema:
--     mysql -u <user> -p <schema> < docs/license/license-tables-update.sql
--
-- It does two things:
--   1. Creates license_customer and license_machine exactly as V1__init_schema.sql defines them
--      (IF NOT EXISTS, so re-running is harmless).
--   2. Updates V1's checksum in flyway_schema_history to match the edited V1 file, so Flyway's
--      startup validation passes (the equivalent of `flyway repair`). The update only applies if
--      the stored checksum is the one of V1 before the license tables were added.
--
-- Checksums were computed with Flyway 11.14.1's own ChecksumCalculator:
--     V1 before the license tables: -1904899439
--     V1 with the license tables:    2121832661
-- If V1__init_schema.sql changes again, these values change too.
--
-- This file is deliberately outside db/migration so Flyway never runs it.
-- ─────────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS license_customer (
    persistent_identity INT PRIMARY KEY,
    customer_name        VARCHAR(256) NOT NULL,
    email                  VARCHAR(255) NOT NULL UNIQUE,
    phone_number             VARCHAR(32) NULL,
    address                    VARCHAR(256) NULL,
    notes                        VARCHAR(1024) NULL,
    passcode_sha1                  VARCHAR(128) NOT NULL,
    license_type                     VARCHAR(1) NOT NULL,
    available_licenses                 INT NOT NULL DEFAULT 0,
    revoked                              BOOLEAN NOT NULL DEFAULT FALSE,
    auto_created                           BOOLEAN NOT NULL DEFAULT FALSE,
    legacy_customer_id                       INT NULL,
    version                                    INT NOT NULL DEFAULT 1,
    date_added                                   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated                                   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS license_machine (
    persistent_identity INT PRIMARY KEY,
    customer_id          INT NOT NULL,
    serial_number          VARCHAR(64) NOT NULL UNIQUE,
    last_paid_quarter        VARCHAR(8) NOT NULL DEFAULT '',
    location_name              VARCHAR(256) NULL,
    build_date                   VARCHAR(32) NULL,
    revoked                        BOOLEAN NOT NULL DEFAULT FALSE,
    client_kind                      VARCHAR(16) NOT NULL DEFAULT 'LEGACY',
    app_version                        VARCHAR(64) NULL,
    app_mode                             VARCHAR(16) NULL,
    first_seen_at                          TIMESTAMP NULL,
    last_heartbeat_at                        TIMESTAMP NULL,
    heartbeat_count                            INT NOT NULL DEFAULT 0,
    last_license_issued_at                       TIMESTAMP NULL,
    legacy_customer_serial_id                      INT NULL,
    version                                          INT NOT NULL DEFAULT 1,
    date_added                                         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated                                         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_license_machine_customer FOREIGN KEY (customer_id)
        REFERENCES license_customer (persistent_identity),
    INDEX idx_license_machine_customer (customer_id)
) ENGINE=InnoDB;

UPDATE flyway_schema_history
SET checksum = 2121832661
WHERE version = '1' AND checksum = -1904899439;

-- Expect checksum = 2121832661. If it still shows another value, the database was created from a
-- different version of V1; run `flyway repair` (or drop and recreate the database) instead.
SELECT version, description, checksum, success
FROM flyway_schema_history
WHERE version = '1';
