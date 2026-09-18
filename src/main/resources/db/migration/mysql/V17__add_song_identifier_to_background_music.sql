-- Adds a (location_id, album_id, song_id) SongIdentifier to background_music_songs and
-- smart_background_music_songs, giving JPA installs a real, ad-hoc-joinable reference back to
-- song_library alongside the existing song_file_path -- mirrors the same unconstrained-column
-- convention already used by song_queue_entries/playlist_songs/user_song_play_history (no FK).
-- smart_background_music_songs also gets a second triple (source_location_id/source_album_id/
-- source_song_id) for the seed song referenced by source_song.
--
-- Clean slate, no production data yet (same ground rule as V4/V7/V8/V9/V14/V16) -- columns are
-- nullable and lazily backfilled by BackgroundMusicServiceImpl as it resolves songs.

ALTER TABLE background_music_songs       ADD COLUMN location_id INT NULL;
ALTER TABLE background_music_songs       ADD COLUMN album_id    INT NULL;
ALTER TABLE background_music_songs       ADD COLUMN song_id     INT NULL;

ALTER TABLE smart_background_music_songs ADD COLUMN location_id        INT NULL;
ALTER TABLE smart_background_music_songs ADD COLUMN album_id           INT NULL;
ALTER TABLE smart_background_music_songs ADD COLUMN song_id            INT NULL;
ALTER TABLE smart_background_music_songs ADD COLUMN source_location_id INT NULL;
ALTER TABLE smart_background_music_songs ADD COLUMN source_album_id    INT NULL;
ALTER TABLE smart_background_music_songs ADD COLUMN source_song_id     INT NULL;
