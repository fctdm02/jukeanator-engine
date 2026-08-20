-- SongIdentifier (used by UserEntity.songPlayHistory and PlaylistEntity.songs, both @ElementCollection
-- tables below) gained a locationId field -- a song's albumId/songId are scan-local integers that
-- are only meaningful together with the location they were scanned from (see
-- docs/multi-tenant-mode.md's documented invariant: "master must never merge multiple slaves'
-- libraries into one ID space").
--
-- Clean slate: this is a deliberate breaking change (confirmed with the user) -- any existing rows
-- predate locationId and cannot be backfilled with a meaningful value, so they're discarded rather
-- than left with a bogus one.
DELETE FROM user_song_play_history;
DELETE FROM playlist_songs;

ALTER TABLE user_song_play_history ADD COLUMN location_id INTEGER;
ALTER TABLE playlist_songs ADD COLUMN location_id INTEGER;
