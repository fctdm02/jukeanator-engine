-- Portable id generation: on Postgres, Hibernate's GenerationType.SEQUENCE maps to a real
-- sequence. See AbstractPersistentEntity.PERSISTENT_IDENTITY_SEQUENCE for the Java side, and
-- db/migration/mysql/V1__init_user_schema.sql for how the same generator is emulated on a
-- database (MySQL) that has no native CREATE SEQUENCE.
CREATE SEQUENCE persistent_identity_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE user_root (
    persistent_identity INTEGER PRIMARY KEY
);

CREATE TABLE users (
    persistent_identity INTEGER PRIMARY KEY,
    user_root_id        INTEGER REFERENCES user_root (persistent_identity),
    first_name          VARCHAR(255) NOT NULL,
    last_name            VARCHAR(255) NOT NULL,
    email_address        VARCHAR(255) NOT NULL UNIQUE,
    password_hash        VARCHAR(255) NOT NULL,
    num_credits          INTEGER NOT NULL,
    role                 VARCHAR(255) NOT NULL
);

CREATE TABLE playlists (
    persistent_identity INTEGER PRIMARY KEY,
    user_id              INTEGER REFERENCES users (persistent_identity),
    owner                VARCHAR(255) NOT NULL,
    name                 VARCHAR(255) NOT NULL
);

CREATE TABLE user_song_play_history (
    user_id    INTEGER NOT NULL REFERENCES users (persistent_identity),
    play_order INTEGER NOT NULL,
    album_id   INTEGER,
    song_id    INTEGER
);

CREATE TABLE user_search_history (
    user_id      INTEGER NOT NULL REFERENCES users (persistent_identity),
    search_order INTEGER NOT NULL,
    search_query VARCHAR(500)
);

CREATE TABLE playlist_songs (
    playlist_id INTEGER NOT NULL REFERENCES playlists (persistent_identity),
    song_order  INTEGER NOT NULL,
    album_id    INTEGER,
    song_id     INTEGER
);
