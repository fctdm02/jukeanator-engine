-- Shares persistent_identity_seq (created in V1__init_user_schema.sql) with every other
-- AbstractPersistentEntity subclass -- see AbstractPersistentEntity.PERSISTENT_IDENTITY_SEQUENCE.
CREATE TABLE locations (
    persistent_identity     INTEGER PRIMARY KEY,
    name                    VARCHAR(255) NOT NULL,
    latitude                DOUBLE PRECISION,
    longitude               DOUBLE PRECISION,
    api_key_hash            VARCHAR(255) NOT NULL,
    status                  VARCHAR(255) NOT NULL,
    last_seen_at            TIMESTAMP,
    library_last_synced_at  TIMESTAMP
);
