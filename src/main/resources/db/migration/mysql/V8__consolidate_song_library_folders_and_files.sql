-- Phase D of the Location/SongLibrary object model + JPA mapping refactor (docs/todo.txt):
-- collapses song_library_folders/song_library_files into a single song_library table -- one
-- SINGLE_TABLE-style schema across the whole FolderEntity/AbstractFileEntity hierarchy (folders
-- AND songs), discriminated by class_discriminator. Album metadata (genre/cover-art-url/
-- record-label/release-date/has-explicit) moves onto the owning ALBUM row's own columns instead
-- of a separate ALBUM_METADATA child row; LOCATION_METADATA rows are gone entirely (that data
-- lives in `location` now, since the Phase B migration). id is application-assigned --
-- SongScanner's single shared counter, unique across an entire scan/location -- not a Hibernate
-- sequence, so there is no separate surrogate "source_id" column anymore.
--
-- Clean slate, no production data yet (same ground rule as V4/V7).

DROP TABLE song_library_files;
DROP TABLE song_library_folders;

CREATE TABLE song_library (
    id                    INT NOT NULL,
    version               INT NOT NULL DEFAULT 1,
    name                  VARCHAR(500) NOT NULL,
    parent_location_id    INT NOT NULL,
    parent_folder_id      INT NULL,             -- NULL only for the ROOT row
    class_discriminator   VARCHAR(20) NOT NULL,  -- ROOT | FOLDER | GENRE | ARTIST | SONG_ARTIST | ALBUM | SONG

    -- SONG columns
    song_artist_name      VARCHAR(500),
    song_name             VARCHAR(500),
    song_track_number     INT,
    song_num_plays        INT,

    -- ALBUM metadata columns (formerly a separate ALBUM_METADATA child row)
    album_genre           VARCHAR(255),
    album_cover_art_url   VARCHAR(1000),
    album_record_label    VARCHAR(255),
    album_release_date    VARCHAR(20),
    album_has_explicit    BOOLEAN,

    PRIMARY KEY (parent_location_id, id),
    CONSTRAINT fk_song_library_location FOREIGN KEY (parent_location_id) REFERENCES location (id),
    CONSTRAINT fk_song_library_parent FOREIGN KEY (parent_location_id, parent_folder_id)
        REFERENCES song_library (parent_location_id, id),
    CONSTRAINT uq_song_library UNIQUE (parent_location_id, parent_folder_id, name)
) ENGINE=InnoDB;

CREATE INDEX ix_song_library_location_name ON song_library (parent_location_id, name);
