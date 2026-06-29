package com.djt.jukeanator_engine.web.event;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songlibrary.event.SongStatisticsChangedEvent;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songplayer.event.AllSongsDonePlayingEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackPausedEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackStartedEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackStoppedEvent;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.event.MultipleSongsAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongQueueChangedEvent;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;

/**
 * Web UI counterpart to {@code JukeANatorEventListener}: rebroadcasts the same
 * domain events over STOMP topics instead of updating Swing components.
 */
@Component
public class WebSocketEventBroadcaster {

  private final SimpMessagingTemplate messagingTemplate;
  private final SongLibraryService songLibraryService;
  private final SongQueueService songQueueService;
  private final SongPlayerService songPlayerService;

  public WebSocketEventBroadcaster(SimpMessagingTemplate messagingTemplate,
      SongLibraryService songLibraryService, SongQueueService songQueueService,
      SongPlayerService songPlayerService) {
    this.messagingTemplate = messagingTemplate;
    this.songLibraryService = songLibraryService;
    this.songQueueService = songQueueService;
    this.songPlayerService = songPlayerService;
  }

  @EventListener
  public void handleSongStatisticsChangedEvent(SongStatisticsChangedEvent event) {
    messagingTemplate.convertAndSend("/topic/genres", songLibraryService.getGenres());
    messagingTemplate.convertAndSend("/topic/popularity", songLibraryService.getMusicByPopularity());
  }

  @EventListener
  public void handleMultipleSongsAddedToQueueEvent(MultipleSongsAddedToQueueEvent event) {
    messagingTemplate.convertAndSend("/topic/popularity", songLibraryService.getMusicByPopularity());
  }

  @EventListener
  public void handleSongQueueChangedEvent(SongQueueChangedEvent event) {
    messagingTemplate.convertAndSend("/topic/queue", event.queuedSongs());
  }

  @EventListener
  public void handlePlaybackStarted(SongPlaybackStartedEvent event) {
    messagingTemplate.convertAndSend("/topic/now-playing",
        new NowPlayingMessage(event.songQueueEntry().getSong()));
    messagingTemplate.convertAndSend("/topic/playback-status", songPlayerService.getPlaybackStatus());
  }

  @EventListener
  public void handlePlaybackPaused(SongPlaybackPausedEvent event) {
    messagingTemplate.convertAndSend("/topic/playback-status", songPlayerService.getPlaybackStatus());
  }

  @EventListener
  public void handleSongPlaybackStoppedEvent(SongPlaybackStoppedEvent event) {
    messagingTemplate.convertAndSend("/topic/now-playing", new NowPlayingMessage(null));
    messagingTemplate.convertAndSend("/topic/playback-status", songPlayerService.getPlaybackStatus());
  }

  @EventListener
  public void handleAllSongsDonePlayingEvent(AllSongsDonePlayingEvent event) {
    messagingTemplate.convertAndSend("/topic/now-playing", new NowPlayingMessage(null));
    messagingTemplate.convertAndSend("/topic/playback-status", songPlayerService.getPlaybackStatus());
  }

  /** Wraps the now-playing song so a "nothing playing" state can be sent as JSON {@code {"song":null}}. */
  public record NowPlayingMessage(SongDto song) {
  }

  @EventListener
  public void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event) {
    messagingTemplate.convertAndSend("/topic/genres", songLibraryService.getGenres());
    messagingTemplate.convertAndSend("/topic/popularity", songLibraryService.getMusicByPopularity());
    messagingTemplate.convertAndSend("/topic/now-playing",
        new NowPlayingMessage(songPlayerService.getNowPlayingSong()));
    messagingTemplate.convertAndSend("/topic/queue", songQueueService.getQueuedSongs());
  }
}
