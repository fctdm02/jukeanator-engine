package com.djt.jukeanator_engine.domain.user.event;

import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;

/**
 * A song a web user queued at {@code locationId} was added to their play history there -- what
 * feeds the Web/Mobile UI's live Recent Plays row (see {@code UserWebSocketBroadcaster}), which
 * shows only the selected location's plays.
 */
public record RecentPlayAddedEvent(String emailAddress, Integer locationId, SongDto song) {
}
