-- Portable id generation, MySQL side: real MySQL has no native CREATE SEQUENCE (that's a
-- Postgres/Oracle/MariaDB feature), so Hibernate's GenerationType.SEQUENCE falls back to emulating
-- one with a backing table -- a single row whose `next_val` column is read-then-incremented on
-- every id allocation. The shape below (table named after the sequence, one BIGINT `next_val`
-- column) is Hibernate's internal org.hibernate.id.enhanced.TableStructure convention; it has not
-- been verified against a live MySQL instance in this environment (no Docker access), so smoke
-- test schema validation (spring.jpa.hibernate.ddl-auto=validate) against a real MySQL container
-- before relying on this in production.
CREATE TABLE persistent_identity_seq (
    next_val BIGINT NOT NULL
) ENGINE=InnoDB;

INSERT INTO persistent_identity_seq (next_val) VALUES (1);

CREATE TABLE users (
    persistent_identity INT PRIMARY KEY,
    first_name           VARCHAR(255) NOT NULL,
    last_name            VARCHAR(255) NOT NULL,
    email_address        VARCHAR(255) NOT NULL UNIQUE,
    password_hash        VARCHAR(255) NOT NULL,
    num_credits          INT NOT NULL,
    role                 VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE playlists (
    persistent_identity INT PRIMARY KEY,
    user_id              INT,
    owner                VARCHAR(255) NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    CONSTRAINT fk_playlists_user FOREIGN KEY (user_id) REFERENCES users (persistent_identity)
) ENGINE=InnoDB;

CREATE TABLE user_song_play_history (
    user_id    INT NOT NULL,
    play_order INT NOT NULL,
    album_id   INT,
    song_id    INT,
    CONSTRAINT fk_song_play_history_user FOREIGN KEY (user_id) REFERENCES users (persistent_identity)
) ENGINE=InnoDB;

CREATE TABLE user_search_history (
    user_id      INT NOT NULL,
    search_order INT NOT NULL,
    search_query VARCHAR(500),
    CONSTRAINT fk_search_history_user FOREIGN KEY (user_id) REFERENCES users (persistent_identity)
) ENGINE=InnoDB;

CREATE TABLE playlist_songs (
    playlist_id INT NOT NULL,
    song_order  INT NOT NULL,
    album_id    INT,
    song_id     INT,
    CONSTRAINT fk_playlist_songs_playlist FOREIGN KEY (playlist_id) REFERENCES playlists (persistent_identity)
) ENGINE=InnoDB;

-- Append-only credit transaction history, owned by the user it belongs to.
CREATE TABLE credit_transactions (
    persistent_identity INT PRIMARY KEY,
    user_id              INT,
    location_id          VARCHAR(255),
    amount                INT NOT NULL,
    type                  VARCHAR(255) NOT NULL,
    timestamp             TIMESTAMP NOT NULL,
    song_album_id         INT,
    song_id               INT,
    resulting_balance     INT NOT NULL,
    CONSTRAINT fk_credit_transactions_user FOREIGN KEY (user_id) REFERENCES users (persistent_identity)
) ENGINE=InnoDB;
