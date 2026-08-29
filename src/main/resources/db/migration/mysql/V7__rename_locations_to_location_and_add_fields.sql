-- Phase A of the Location/SongLibrary object model + JPA mapping refactor (docs/todo.txt):
-- LocationEntity gains logoName/isGeoFenced, and this table's PK column is renamed to "id" per
-- this refactor's naming convention for the location/song_library tables specifically -- every
-- other AbstractPersistentEntity-backed table (users, song queue entries, background music, ...)
-- is untouched and keeps its existing persistent_identity column name.
--
-- Clean slate, no production data yet (same ground rule as V4) -- existing rows are simply
-- widened/renamed in place rather than migrated with any data transformation.

-- Every existing FK referencing locations(persistent_identity) must be dropped and re-pointed at
-- the renamed column so this migration leaves the database in a valid state on its own --
-- song_library_folders/song_library_files (wholesale replaced by the future song_library table in
-- a later phase of this refactor, but still present at this point) and song_queue_entries (V5),
-- untouched by the rest of this refactor but still a real FK dependent on this column rename.
ALTER TABLE song_library_folders DROP FOREIGN KEY fk_song_library_folders_location;
ALTER TABLE song_library_files DROP FOREIGN KEY fk_song_library_files_location;
ALTER TABLE song_queue_entries DROP FOREIGN KEY fk_song_queue_entries_location;

RENAME TABLE locations TO location;
ALTER TABLE location CHANGE COLUMN persistent_identity id INT;
ALTER TABLE location ADD COLUMN version INT NOT NULL DEFAULT 1;
ALTER TABLE location ADD COLUMN logo_name VARCHAR(255);
ALTER TABLE location ADD COLUMN is_geo_fenced BOOLEAN;
ALTER TABLE location ADD CONSTRAINT uq_location_name UNIQUE (name);

ALTER TABLE song_library_folders
    ADD CONSTRAINT fk_song_library_folders_location FOREIGN KEY (location_id) REFERENCES location (id);
ALTER TABLE song_library_files
    ADD CONSTRAINT fk_song_library_files_location FOREIGN KEY (location_id) REFERENCES location (id);
ALTER TABLE song_queue_entries
    ADD CONSTRAINT fk_song_queue_entries_location FOREIGN KEY (location_id) REFERENCES location (id);
