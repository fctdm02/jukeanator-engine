-- AbstractEntity gained a `version` field (moved up from AbstractPersistentEntity, so
-- AbstractAssociativeEntity subclasses get one too -- see the Location/SongLibrary refactor,
-- docs/todo.txt). location already got its version column in
-- V7__rename_locations_to_location_and_add_fields.sql; this migration catches up every other
-- @Entity that extends AbstractPersistentEntity and therefore now expects Hibernate's
-- ddl-auto: validate to find a matching version column.
--
-- Clean slate, no production data yet (same ground rule as V4/V7/V8).

ALTER TABLE users ADD COLUMN version INT NOT NULL DEFAULT 1;
ALTER TABLE playlists ADD COLUMN version INT NOT NULL DEFAULT 1;
ALTER TABLE credit_transactions ADD COLUMN version INT NOT NULL DEFAULT 1;
ALTER TABLE background_music_songs ADD COLUMN version INT NOT NULL DEFAULT 1;
ALTER TABLE smart_background_music_songs ADD COLUMN version INT NOT NULL DEFAULT 1;
