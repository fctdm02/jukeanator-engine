-- Background-music storage, JPA-backed. background_music_songs and smart_background_music_songs
-- are TABLE_PER_CLASS-mapped from BackgroundMusicSongEntity/SmartBackgroundMusicSongEntity -- see
-- BackgroundMusicRepositoryJpaImpl's class javadoc. Each table is a complete standalone schema
-- (no shared parent table, no discriminator column), mirroring the pre-existing split between
-- BackgroundMusicSongs.json and SmartBackgroundMusicSongs.json. Shares persistent_identity_seq
-- (created in V1__init_user_schema.sql) with every other AbstractPersistentEntity-style row.
CREATE TABLE background_music_songs (
    persistent_identity INT PRIMARY KEY,
    song_file_path        VARCHAR(1000) NOT NULL,
    time_last_played        TIMESTAMP NULL,
    number_of_plays           INT NOT NULL
) ENGINE=InnoDB;

CREATE TABLE smart_background_music_songs (
    persistent_identity INT PRIMARY KEY,
    song_file_path        VARCHAR(1000) NOT NULL,
    time_last_played        TIMESTAMP NULL,
    number_of_plays           INT NOT NULL,
    source_song                 VARCHAR(1000),
    source_song_num_plays         INT,
    reason                          VARCHAR(255) NOT NULL
) ENGINE=InnoDB;
