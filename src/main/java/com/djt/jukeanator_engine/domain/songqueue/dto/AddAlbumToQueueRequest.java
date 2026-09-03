package com.djt.jukeanator_engine.domain.songqueue.dto;

public record AddAlbumToQueueRequest(String username, Integer albumId, Integer priority) {
}
