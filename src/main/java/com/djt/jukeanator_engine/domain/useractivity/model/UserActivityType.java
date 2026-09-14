package com.djt.jukeanator_engine.domain.useractivity.model;

/**
 * The kind of user interaction a {@link UserActivityRecord} captures. The
 * {@code TAB_NAVIGATION}/{@code PAGE_NAVIGATION}/{@code SEARCH_*}/{@code *_VIEWED} types are
 * Swing/JFC-only (see {@link UserActivitySource#SWING_UI}); the {@code QUEUE_*} types are recorded
 * for both {@link UserActivitySource#SWING_UI} and {@link UserActivitySource#MOBILE_WEB}.
 */
public enum UserActivityType {
  TAB_NAVIGATION,
  PAGE_NAVIGATION,
  SEARCH_QUERY,
  SEARCH_RESULT_CLICK,
  ARTIST_VIEWED,
  ALBUM_VIEWED,
  QUEUE_SONG_ADDED,
  QUEUE_PRIORITY_SONG_ADDED,
  QUEUE_SONG_REMOVED,
  QUEUE_SONG_MOVED_UP,
  QUEUE_SONG_MOVED_DOWN
}
