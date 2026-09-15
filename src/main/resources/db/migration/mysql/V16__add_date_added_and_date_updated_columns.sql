-- Every @Entity now carries dateAdded/dateUpdated (Hibernate @CreationTimestamp/@UpdateTimestamp),
-- stamped automatically on insert/update -- see AbstractPersistentEntity for the tables that
-- inherit them, and SongLibraryJpaEntity/UserActivityEntity/SongQueueEntryJpaEntity for the
-- standalone (non-AbstractPersistentEntity) tables that declare them directly.
--
-- Clean slate, no production data yet (same ground rule as V4/V7/V8/V9/V14) -- DEFAULT
-- CURRENT_TIMESTAMP only backstops any local dev/test rows; Hibernate sets both columns
-- explicitly on every insert/update going forward.

ALTER TABLE song_library                 ADD COLUMN date_added   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE song_library                 ADD COLUMN date_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE user_activity                ADD COLUMN date_added   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE user_activity                ADD COLUMN date_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE song_queue_entries           ADD COLUMN date_added   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE song_queue_entries           ADD COLUMN date_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE users                        ADD COLUMN date_added   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users                        ADD COLUMN date_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE playlists                    ADD COLUMN date_added   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE playlists                    ADD COLUMN date_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE mobile_transactions          ADD COLUMN date_added   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE mobile_transactions          ADD COLUMN date_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE local_cash_transactions      ADD COLUMN date_added   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE local_cash_transactions      ADD COLUMN date_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE local_credit_transactions    ADD COLUMN date_added   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE local_credit_transactions    ADD COLUMN date_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE jukebox_split_period         ADD COLUMN date_added   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE jukebox_split_period         ADD COLUMN date_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE location                     ADD COLUMN date_added   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE location                     ADD COLUMN date_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE background_music_songs       ADD COLUMN date_added   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE background_music_songs       ADD COLUMN date_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE smart_background_music_songs ADD COLUMN date_added   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE smart_background_music_songs ADD COLUMN date_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
