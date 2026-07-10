package com.djt.jukeanator_engine.domain.backgroundmusic.model;

/**
 * The reason a particular song was selected as a "smart addition" candidate.
 *
 * @author tmyers
 */
public enum SmartAdditionReason {
  SAME_ARTIST,
  SAME_ALBUM,
  POPULAR_SONG_FROM_GENRE,
  SONG_FROM_FAVORITE_ALBUM
}
