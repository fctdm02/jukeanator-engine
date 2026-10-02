package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.util.List;

/**
 * Asks whether each song could be queued right now at the given {@code priority} -- used by the
 * web UI's playlist Multi-Select Mode to vet a song before letting the user select it. Each song is
 * checked independently against the current queue; the eventual {@code addMultipleSongs} call
 * still re-checks every song as the queue grows.
 */
public record CheckSongsEligibilityRequest(List<SongIdentifier> songIdentifiers,
    Integer priority) {
}
