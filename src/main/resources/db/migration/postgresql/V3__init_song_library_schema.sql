-- Multi-tenant song library storage: every location's catalog lives in these two tables,
-- tenant-separated by location_id. Shares persistent_identity_seq (created in
-- V1__init_user_schema.sql) with every other AbstractPersistentEntity-style row.
--
-- Two SINGLE_TABLE-inheritance-style tables, one per entity hierarchy in
-- com.djt.jukeanator_engine.domain.songlibrary.model: FolderEntity (Root/Genre/Artist/Album) and
-- AbstractFileEntity (Song/AlbumCoverArt/AlbumMetadata/LocationMetadata). See
-- SongLibraryRepositoryJpaImpl for how rows are assembled back into a RootFolderEntity tree.
--
-- The literal "PRIMARY KEY (LocationId, AlbumId, SongId)" design intent is delivered via the
-- UNIQUE constraint below rather than as the JPA @Id: folder rows (root/genre/artist) have no
-- natural (locationId, albumId, songId) tuple, so a generated persistent_identity is the real
-- key, exactly like every other AbstractPersistentEntity subclass already in this schema.
--
-- source_id/parent references are scan-local ids assigned by SongScanner on the owning slave --
-- never merged or reused across locations. See docs/multi-tenant-mode.md's documented invariant:
-- "master must never merge multiple slaves' libraries into one ID space."

CREATE TABLE song_library_folders (
    persistent_identity INTEGER PRIMARY KEY,
    location_id         INTEGER NOT NULL REFERENCES locations (persistent_identity),
    folder_type          VARCHAR(20) NOT NULL,   -- ROOT | GENRE | ARTIST | ALBUM
    parent_folder_id      INTEGER REFERENCES song_library_folders (persistent_identity),
    source_id              INTEGER,               -- scan-local id; NULL for ROOT
    name                    VARCHAR(500) NOT NULL,
    CONSTRAINT uq_song_library_folders UNIQUE (location_id, folder_type, source_id)
);

CREATE INDEX ix_song_library_folders_parent ON song_library_folders (parent_folder_id);
CREATE INDEX ix_song_library_folders_location ON song_library_folders (location_id, folder_type);

CREATE TABLE song_library_files (
    persistent_identity INTEGER PRIMARY KEY,
    location_id         INTEGER NOT NULL REFERENCES locations (persistent_identity),
    parent_folder_id     INTEGER NOT NULL REFERENCES song_library_folders (persistent_identity),
    file_type             VARCHAR(20) NOT NULL,  -- SONG | ALBUM_COVER_ART | ALBUM_METADATA | LOCATION_METADATA
    source_id              INTEGER,               -- scan-local song id; NULL for the 3 singleton file types
    name                    VARCHAR(500) NOT NULL,

    -- SONG columns
    artist_name              VARCHAR(500),
    song_name                 VARCHAR(500),
    track_number                INTEGER,
    num_plays                    INTEGER,

    -- ALBUM_METADATA columns
    genre                         VARCHAR(255),
    cover_art_url                  VARCHAR(1000),
    record_label                    VARCHAR(255),
    release_date                     VARCHAR(20),
    has_explicit                       BOOLEAN,

    -- LOCATION_METADATA columns
    location_name                      VARCHAR(255),
    logo_name                            VARCHAR(255),
    latitude                              DOUBLE PRECISION,
    longitude                              DOUBLE PRECISION,
    is_geo_fenced                            BOOLEAN,

    CONSTRAINT uq_song_library_files UNIQUE (location_id, parent_folder_id, file_type, source_id)
);

CREATE INDEX ix_song_library_files_parent ON song_library_files (parent_folder_id, file_type);
CREATE INDEX ix_song_library_files_location ON song_library_files (location_id);
