package com.djt.jukeanator_engine.web.event;

import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;

/**
 * A song the patron just queued at {@code locationId}; the Web/Mobile UI adds it to its live
 * Recent Plays row only while that location is selected.
 */
public record RecentPlayMessage(Integer locationId, SongDto song) {
}
