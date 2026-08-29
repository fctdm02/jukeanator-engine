-- SongLibraryRepositoryJpaImpl.storeAggregateRoot() deletes every row for a location in one bulk
-- statement ("delete from SongLibraryJpaEntity where locationId = :locationId"), then reinserts
-- the whole tree. Against the self-referencing fk_song_library_parent constraint added in V8,
-- MySQL does not guarantee children are removed before their parents within that single bulk
-- DELETE, so it can fail with "Cannot delete or update a parent row: a foreign key constraint
-- fails" depending on row order. Since a location's rows are always deleted as a whole subtree
-- (never a partial one), ON DELETE CASCADE is the correct fix: removing any row cascades to
-- everything beneath it, which is exactly the semantics storeAggregateRoot already relies on.
ALTER TABLE song_library DROP FOREIGN KEY fk_song_library_parent;
ALTER TABLE song_library
    ADD CONSTRAINT fk_song_library_parent FOREIGN KEY (parent_location_id, parent_folder_id)
        REFERENCES song_library (parent_location_id, id) ON DELETE CASCADE;
