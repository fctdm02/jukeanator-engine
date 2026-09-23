-- Consolidated baseline schema. This project has not been deployed to production, so the prior
-- V1-V17 migration history (renames, drops, incremental column adds) -- and the later financial
-- ledger refactor that had briefly lived in its own V2 migration -- have been collapsed into this
-- single creation script reflecting the current, final schema shape. Add seed-data INSERT
-- statements below the DDL as needed before the initial production deployment.

-- Hibernate's GenerationType.SEQUENCE emulation table (org.hibernate.id.enhanced.TableStructure):
-- real MySQL has no native CREATE SEQUENCE, so every AbstractPersistentEntity subclass shares this
-- single row, read-then-incremented on every id allocation.
CREATE TABLE persistent_identity_seq (
    next_val BIGINT NOT NULL
) ENGINE=InnoDB;

INSERT INTO persistent_identity_seq (next_val) VALUES (1);

CREATE TABLE user_account (
    persistent_identity INT PRIMARY KEY,
    first_name          VARCHAR(255) NOT NULL,
    last_name            VARCHAR(255) NOT NULL,
    email_address         VARCHAR(255) NOT NULL UNIQUE,
    password_hash          VARCHAR(255) NOT NULL,
    num_credits              INT NOT NULL,
    role                       VARCHAR(255) NOT NULL,
    version                     INT NOT NULL DEFAULT 1,
    date_added                    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated                    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Per-location config, synced from each slave's own application.yml over the /ws-slave STOMP
-- connection. Uses "id" (not "persistent_identity") as its PK column name -- location and
-- song_library are the only two tables that diverge from the default naming convention.
CREATE TABLE location (
    id                        INT PRIMARY KEY,
    name                       VARCHAR(255) NOT NULL,
    latitude                    DOUBLE,
    longitude                    DOUBLE,
    api_key_hash                  VARCHAR(255) NOT NULL,
    status                          VARCHAR(255) NOT NULL,
    last_seen_at                      TIMESTAMP NULL,
    library_last_synced_at              TIMESTAMP NULL,
    version                               INT NOT NULL DEFAULT 1,
    logo_name                              VARCHAR(255),
    is_geo_fenced                            BOOLEAN,
    priority_cost_multiplier                   INT,
    credits_per_dollar                           INT,
    five_dollar_bonus_credits                      INT,
    ten_dollar_bonus_credits                         INT,
    web_cost_multiplier                                INT,
    display_currency_for_cost                            BOOLEAN,
    date_added                                             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated                                             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_location_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE user_playlist (
    persistent_identity INT PRIMARY KEY,
    user_id              INT,
    owner                  VARCHAR(255) NOT NULL,
    name                     VARCHAR(255) NOT NULL,
    version                    INT NOT NULL DEFAULT 1,
    date_added                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_playlist_user_account FOREIGN KEY (user_id) REFERENCES user_account (persistent_identity)
) ENGINE=InnoDB;

-- @ElementCollection tables (UserEntity.songPlayHistory / .searchHistory, PlaylistEntity.songs)
-- have no surrogate PK of their own. album_id/song_id/location_id are scan-local ids meaningful
-- only together (see docs/multi-tenant-mode.md) and are deliberately left unconstrained (no FK) --
-- they may reference songs from a location whose library has since been rescanned or removed.
CREATE TABLE user_song_play_history (
    user_id    INT NOT NULL,
    play_order  INT NOT NULL,
    album_id     INT,
    song_id       INT,
    location_id    INT,
    CONSTRAINT fk_song_play_history_user_account FOREIGN KEY (user_id) REFERENCES user_account (persistent_identity)
) ENGINE=InnoDB;

CREATE TABLE user_search_history (
    user_id      INT NOT NULL,
    search_order  INT NOT NULL,
    search_query   VARCHAR(500),
    CONSTRAINT fk_search_history_user_account FOREIGN KEY (user_id) REFERENCES user_account (persistent_identity)
) ENGINE=InnoDB;

CREATE TABLE user_playlist_song (
    playlist_id INT NOT NULL,
    song_order   INT NOT NULL,
    album_id      INT,
    song_id        INT,
    location_id     INT,
    CONSTRAINT fk_user_playlist_song_playlist FOREIGN KEY (playlist_id) REFERENCES user_playlist (persistent_identity)
) ENGINE=InnoDB;

-- Append-only user-spend credit history: a user spending already-owned song credits to queue a
-- song at a location (mobile/web queue spends), owned by the user it belongs to. Never written to
-- by the local walk-up (JFC/Swing) user.
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
    CONSTRAINT fk_user_song_credit_usage_user_account FOREIGN KEY (user_id) REFERENCES user_account (persistent_identity)
) ENGINE=InnoDB;

-- Append-only record of a user obtaining song credits with real money (via PaymentGateway, e.g.
-- Braintree) -- never location-attributed, unlike user_song_credit_usage above.
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
    CONSTRAINT fk_user_add_funds_transaction_user_account FOREIGN KEY (user_id) REFERENCES user_account (persistent_identity)
) ENGINE=InnoDB;

-- Multi-tenant song library storage: every location's catalog lives in this one table,
-- tenant-separated by parent_location_id, discriminated by class_discriminator (ROOT | FOLDER |
-- GENRE | ARTIST | SONG_ARTIST | ALBUM | SONG). id is application-assigned by SongScanner's single
-- shared counter, unique per location -- not a Hibernate sequence. ON DELETE CASCADE on the
-- self-referencing FK is required by SongLibraryRepositoryJpaImpl.storeAggregateRoot(), which
-- bulk-deletes an entire location's subtree in one statement (MySQL doesn't guarantee child rows
-- are removed before their parents within that single DELETE).
CREATE TABLE song_library (
    id                    INT NOT NULL,
    version                INT NOT NULL DEFAULT 1,
    name                     VARCHAR(500) NOT NULL,
    parent_location_id        INT NOT NULL,
    parent_folder_id            INT NULL,
    class_discriminator            VARCHAR(20) NOT NULL,
    song_artist_name                  VARCHAR(500),
    song_name                           VARCHAR(500),
    song_track_number                     INT,
    song_num_plays                          INT,
    album_genre                               VARCHAR(255),
    album_cover_art_url                         VARCHAR(1000),
    album_record_label                            VARCHAR(255),
    album_release_date                              VARCHAR(20),
    album_has_explicit                                BOOLEAN,
    date_added                                          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated                                          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (parent_location_id, id),
    CONSTRAINT fk_song_library_location FOREIGN KEY (parent_location_id) REFERENCES location (id)
        ON UPDATE CASCADE,
    CONSTRAINT fk_song_library_parent FOREIGN KEY (parent_location_id, parent_folder_id)
        REFERENCES song_library (parent_location_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_song_library UNIQUE (parent_location_id, parent_folder_id, name)
) ENGINE=InnoDB;

CREATE INDEX ix_song_library_location_name ON song_library (parent_location_id, name);

-- Holds every location's live queue -- there is no separate "song queue root" row;
-- SongQueueRootEntity is reassembled in memory from these rows ordered by queue_order.
CREATE TABLE song_queue_entries (
    persistent_identity INT PRIMARY KEY,
    location_id          INT NOT NULL,
    album_id                INT NOT NULL,
    song_id                   INT NOT NULL,
    queue_order                 INT NOT NULL,
    username                      VARCHAR(255) NOT NULL,
    priority                        INT NOT NULL,
    queued_at_time                    TIMESTAMP NOT NULL,
    date_added                          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated                          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_song_queue_entries_location FOREIGN KEY (location_id) REFERENCES location (id)
) ENGINE=InnoDB;

CREATE INDEX ix_song_queue_entries_location_order ON song_queue_entries (location_id, queue_order);

-- Background-music storage, SINGLE_TABLE-mapped from BackgroundMusicSongEntity /
-- SmartBackgroundMusicSongEntity (like location_transaction), discriminated by type: 'REGULAR' for
-- configured background-music songs, 'SMART' for dynamically selected smart additions. The
-- smart-only columns (source_song, source_song_num_plays, reason, source_*_id) are NULL on REGULAR
-- rows; the CHECK constraint still requires a reason on every SMART row. location_id/album_id/
-- song_id (and source_location_id/source_album_id/source_song_id) give JPA installs an ad-hoc,
-- unconstrained (no FK) reference back to song_library alongside song_file_path (and source_song),
-- lazily backfilled by BackgroundMusicServiceImpl as it resolves songs.
CREATE TABLE song_background_music (
    persistent_identity INT PRIMARY KEY,
    type                  VARCHAR(20) NOT NULL,
    song_file_path          VARCHAR(1000) NOT NULL,
    time_last_played          TIMESTAMP NULL,
    number_of_plays             INT NOT NULL,
    source_song                   VARCHAR(1000) NULL,
    source_song_num_plays           INT NULL,
    reason                            VARCHAR(255) NULL,
    version                             INT NOT NULL DEFAULT 1,
    date_added                           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated                           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    location_id                              INT NULL,
    album_id                                   INT NULL,
    song_id                                      INT NULL,
    source_location_id                             INT NULL,
    source_album_id                                  INT NULL,
    source_song_id                                     INT NULL,
    CONSTRAINT ck_song_background_music_smart_reason CHECK (type <> 'SMART' OR reason IS NOT NULL)
) ENGINE=InnoDB;

-- Financial Ledger / jukebox-split feature. Freestanding (not owned by another aggregate), but
-- tenant-separated by parent_location_id (like song_library) so a slave's splits can later be
-- synced into master's database alongside every other location's.
CREATE TABLE location_jukebox_split (
    persistent_identity INT PRIMARY KEY,
    version               INT NOT NULL DEFAULT 1,
    parent_location_id      INT NOT NULL,
    start_date              TIMESTAMP NOT NULL,
    end_date                  TIMESTAMP NULL,
    split_percentage_to_owner   INT NULL,
    cash_total                    DECIMAL(12,2) NULL,
    card_total                      DECIMAL(12,2) NULL,
    mobile_total                      DECIMAL(12,2) NULL,
    total_earned                        DECIMAL(12,2) NULL,
    amount_due_owner                      DECIMAL(12,2) NULL,
    amount_due_operator                     DECIMAL(12,2) NULL,
    date_added                                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated                                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_location_jukebox_split_location FOREIGN KEY (parent_location_id) REFERENCES location (id)
        ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE INDEX ix_location_jukebox_split_location ON location_jukebox_split (parent_location_id);

-- Local (bill-acceptor / credit-card-reader) cash-award history, merged into one table
-- discriminated by transaction_type (CASH | CREDIT_CARD -- see
-- AbstractLocationTransactionEntity's single-table JPA inheritance) so a central DBA can see both
-- streams together and filter by location. source_transaction_id is the slave-to-master mirror's
-- idempotency key: NULL for every transaction recorded directly on the instance that owns it (the
-- normal case on a slave/standalone instance); only master-received mirrored rows ever populate
-- it, carrying the slave's own local persistent_identity for that transaction. The composite
-- unique key only meaningfully constrains mirrored rows -- MySQL treats any row containing a NULL
-- indexed column as distinct from every other row in a unique index, so genuinely local
-- transactions never collide with each other.
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

-- Append-only user-activity log (Swing/JFC desktop UI navigation + queue actions, mobile/web queue
-- actions). No update/delete path -- UserActivityRepositoryJpaImpl only ever inserts.
CREATE TABLE user_activity (
    persistent_identity INT PRIMARY KEY,
    location_id          INT NULL,
    source                 VARCHAR(32) NOT NULL,
    username                 VARCHAR(255) NOT NULL,
    activity_type              VARCHAR(64) NOT NULL,
    occurred_at                  TIMESTAMP NOT NULL,
    details                        JSON NULL,
    date_added                       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_updated                       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE INDEX idx_user_activity_location_occurred_at ON user_activity (location_id, occurred_at);
CREATE INDEX idx_user_activity_activity_type ON user_activity (activity_type);
