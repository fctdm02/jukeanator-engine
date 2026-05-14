package com.djt.jukeanator_engine.domain.songplayer.dto;

public record SongPlaybackStatusDto(

    SongPlayerStatus status,

    Long elapsedSeconds,

    Long totalSeconds) {
}
