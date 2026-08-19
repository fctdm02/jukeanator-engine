-- Shares the persistent_identity_seq emulation table (created in V1__init_user_schema.sql) with
-- every other AbstractPersistentEntity subclass -- see
-- AbstractPersistentEntity.PERSISTENT_IDENTITY_SEQUENCE.
CREATE TABLE locations (
    persistent_identity     INT PRIMARY KEY,
    name                    VARCHAR(255) NOT NULL,
    latitude                DOUBLE,
    longitude               DOUBLE,
    api_key_hash            VARCHAR(255) NOT NULL,
    status                  VARCHAR(255) NOT NULL,
    last_seen_at            TIMESTAMP NULL,
    library_last_synced_at  TIMESTAMP NULL
) ENGINE=InnoDB;
