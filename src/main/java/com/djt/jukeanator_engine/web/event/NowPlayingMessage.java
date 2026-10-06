package com.djt.jukeanator_engine.web.event;

import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;

/**
 * Wraps the now-playing song so a "nothing playing" state can be sent as JSON
 * {@code {"song":null}} (see {@link LocationTopics#nowPlaying}).
 */
public record NowPlayingMessage(SongDto song) {
}
