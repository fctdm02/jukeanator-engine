package com.djt.jukeanator_engine.web.event;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.djt.jukeanator_engine.config.NotMasterModeCondition;
import com.djt.jukeanator_engine.domain.common.security.LocalPrincipal;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songlibrary.event.SongStatisticsChangedEvent;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songplayer.event.AllSongsDonePlayingEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackPausedEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackStartedEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackStoppedEvent;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.event.MultipleSongsAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongQueueChangedEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongQueueEmptyEvent;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import java.util.List;
import com.djt.jukeanator_engine.domain.user.event.UserCreditsChangedEvent;

/**
 * Web UI counterpart to {@code JukeANatorEventListener}: rebroadcasts the same
 * domain events over STOMP topics instead of updating Swing components.
 */
@Component
@Conditional(NotMasterModeCondition.class)
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
    Integer locationId = songLibraryService.getOwnLocationId();
    messagingTemplate.convertAndSend("/topic/genres", songLibraryService.getGenres(locationId));
    messagingTemplate.convertAndSend("/topic/popularity",
        songLibraryService.getMusicByPopularity(locationId));
  }

  @EventListener
  public void handleMultipleSongsAddedToQueueEvent(MultipleSongsAddedToQueueEvent event) {
    messagingTemplate.convertAndSend("/topic/popularity",
        songLibraryService.getMusicByPopularity(songLibraryService.getOwnLocationId()));
  }

  @EventListener
  public void handleSongQueueChangedEvent(SongQueueChangedEvent event) {
    messagingTemplate.convertAndSend("/topic/queue", event.queuedSongs());
  }

  /**
   * Dequeuing from an already-empty queue skips {@link SongQueueChangedEvent} entirely (see
   * {@code SongQueueServiceImpl.dequeueNextSong()}), so this is the only signal that reaches the
   * web UI in that case. Broadcast on the same {@code /topic/queue} topic as an empty list so the
   * frontend's single subscription handles both "queue changed" and "queue is now empty".
   */
  @EventListener
  public void handleSongQueueEmptyEvent(SongQueueEmptyEvent event) {
    messagingTemplate.convertAndSend("/topic/queue", List.of());
  }

  @EventListener
  public void handlePlaybackStarted(SongPlaybackStartedEvent event) {
    messagingTemplate.convertAndSend("/topic/now-playing",
        new NowPlayingMessage(event.songQueueEntry().song()));
    messagingTemplate.convertAndSend("/topic/playback-status", songPlayerService.getPlaybackStatus(songLibraryService.getOwnLocationId()));
  }

  @EventListener
  public void handlePlaybackPaused(SongPlaybackPausedEvent event) {
    messagingTemplate.convertAndSend("/topic/playback-status", songPlayerService.getPlaybackStatus(songLibraryService.getOwnLocationId()));
  }

  @EventListener
  public void handleSongPlaybackStoppedEvent(SongPlaybackStoppedEvent event) {
    messagingTemplate.convertAndSend("/topic/now-playing", new NowPlayingMessage(null));
    messagingTemplate.convertAndSend("/topic/playback-status", songPlayerService.getPlaybackStatus(songLibraryService.getOwnLocationId()));
  }

  @EventListener
  public void handleAllSongsDonePlayingEvent(AllSongsDonePlayingEvent event) {
    messagingTemplate.convertAndSend("/topic/now-playing", new NowPlayingMessage(null));
    messagingTemplate.convertAndSend("/topic/playback-status", songPlayerService.getPlaybackStatus(songLibraryService.getOwnLocationId()));
  }

  @EventListener
  public void handleSongAddedToQueueEvent(SongAddedToQueueEvent event) {
    String username = event.queueEntry().username();
    if (!LocalPrincipal.LOCAL_USERNAME.equals(username)) {
      messagingTemplate.convertAndSendToUser(
          username, "/queue/recent-plays", event.queueEntry().song());
    }
  }

  @EventListener
  public void handleUserCreditsChangedEvent(UserCreditsChangedEvent event) {
    messagingTemplate.convertAndSendToUser(
        event.emailAddress(), "/queue/credits", new CreditsMessage(event.numCredits()));
  }

  /** Wraps the now-playing song so a "nothing playing" state can be sent as JSON {@code {"song":null}}. */
  public record NowPlayingMessage(SongDto song) {}

  public record CreditsMessage(int numCredits) {}

  @EventListener
  public void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event) {
    Integer locationId = songLibraryService.getOwnLocationId();
    messagingTemplate.convertAndSend("/topic/genres", songLibraryService.getGenres(locationId));
    messagingTemplate.convertAndSend("/topic/popularity",
        songLibraryService.getMusicByPopularity(locationId));
    messagingTemplate.convertAndSend("/topic/now-playing",
        new NowPlayingMessage(songPlayerService.getNowPlayingSong(locationId)));
    messagingTemplate.convertAndSend("/topic/queue", songQueueService.getQueuedSongs(locationId));
  }
}
