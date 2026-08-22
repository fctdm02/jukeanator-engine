-- Song queue storage, JPA-backed. song_queue_entries holds every location's live queue -- there
-- is no separate "song queue root" row: SongQueueRootEntity is reassembled in memory from these
-- rows (ordered by queue_order) and is never itself JPA-mapped, exactly like UserRootEntity's
-- users table. Shares persistent_identity_seq (created in V1__init_user_schema.sql) with every
-- other AbstractPersistentEntity-style row.
--
-- Unlike users (keyed by email_address) or locations, a queued song has no natural identity of
-- its own -- the same (location_id, album_id, song_id) pair can validly appear more than once at
-- different priorities/times -- so SongQueueRepositoryJpaImpl.storeAggregateRoot() deletes every
-- row for a location and reinserts the current in-memory queue on every mutation. queue_order
-- records each entry's list position explicitly, since order can change (via
-- moveSongUpInQueue/moveSongDownInQueue/randomizeQueue) independent of priority/queued_at_time and
-- so cannot be reconstructed from those columns alone on reload.
CREATE TABLE song_queue_entries (
    persistent_identity INTEGER PRIMARY KEY,
    location_id         INTEGER NOT NULL REFERENCES locations (persistent_identity),
    album_id              INTEGER NOT NULL,
    song_id                 INTEGER NOT NULL,
    queue_order               INTEGER NOT NULL,
    username                    VARCHAR(255) NOT NULL,
    priority                      INTEGER NOT NULL,
    queued_at_time                  TIMESTAMP NOT NULL
);

CREATE INDEX ix_song_queue_entries_location_order ON song_queue_entries (location_id, queue_order);
