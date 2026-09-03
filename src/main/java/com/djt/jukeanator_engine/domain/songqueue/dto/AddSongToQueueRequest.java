package com.djt.jukeanator_engine.domain.songqueue.dto;

public record AddSongToQueueRequest(String username, Integer albumId, Integer songId,
    Integer priority) {
}
