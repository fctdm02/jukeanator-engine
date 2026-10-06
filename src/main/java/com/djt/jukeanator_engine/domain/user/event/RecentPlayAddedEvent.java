package com.djt.jukeanator_engine.domain.user.event;

import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;

/**
 * A song a web user queued was added to their play history -- what feeds the Web/Mobile UI's live
 * Recent Plays row (see {@code UserWebSocketBroadcaster}).
 */
public record RecentPlayAddedEvent(String emailAddress, SongDto song) {
}
