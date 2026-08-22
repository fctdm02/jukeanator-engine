-- Background-music storage, JPA-backed. background_music_songs and smart_background_music_songs
-- are TABLE_PER_CLASS-mapped from BackgroundMusicSongEntity/SmartBackgroundMusicSongEntity -- see
-- BackgroundMusicRepositoryJpaImpl's class javadoc. Each table is a complete standalone schema
-- (no shared parent table, no discriminator column), mirroring the pre-existing split between
-- BackgroundMusicSongs.json and SmartBackgroundMusicSongs.json. Shares persistent_identity_seq
-- (created in V1__init_user_schema.sql) with every other AbstractPersistentEntity-style row.
CREATE TABLE background_music_songs (
    persistent_identity INTEGER PRIMARY KEY,
    song_file_path       VARCHAR(1000) NOT NULL,
    time_last_played       TIMESTAMP,
    number_of_plays          INTEGER NOT NULL
);

CREATE TABLE smart_background_music_songs (
    persistent_identity INTEGER PRIMARY KEY,
    song_file_path       VARCHAR(1000) NOT NULL,
    time_last_played       TIMESTAMP,
    number_of_plays          INTEGER NOT NULL,
    source_song                VARCHAR(1000),
    source_song_num_plays        INTEGER,
    reason                         VARCHAR(255) NOT NULL
);
