package com.djt.jukeanator_engine.domain.songqueue.dto;

import java.util.List;

public record AddMultipleSongsToQueueRequest(String username, List<SongIdentifier> songIdentifiers,
    Integer priority) {
}
