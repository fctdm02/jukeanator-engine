package com.djt.jukeanator_engine.web.event;

import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.config.NotMasterModeCondition;
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
import com.djt.jukeanator_engine.domain.songqueue.event.SongQueueEmptyEvent;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

/**
 * Web UI counterpart to {@code JukeANatorEventListener}: rebroadcasts this instance's own
 * location's domain events over STOMP topics instead of updating Swing components. The queue and
 * player topics are the location-scoped {@link LocationTopics}, exactly as master publishes them
 * for a slave's location, so the Web/Mobile UI subscribes the same way in every {@code app.mode}.
 * A patron's own credits and recent plays are sent by {@link UserWebSocketBroadcaster}.
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
    messagingTemplate.convertAndSend(LocationTopics.queue(ownLocationId()), event.queuedSongs());
  }

  /**
   * Dequeuing from an already-empty queue skips {@link SongQueueChangedEvent} entirely (see
   * {@code SongQueueServiceImpl.dequeueNextSong()}), so this is the only signal that reaches the
   * web UI in that case. Broadcast on the same queue topic as an empty list so the frontend's
   * single subscription handles both "queue changed" and "queue is now empty".
   */
  @EventListener
  public void handleSongQueueEmptyEvent(SongQueueEmptyEvent event) {
    messagingTemplate.convertAndSend(LocationTopics.queue(ownLocationId()), List.of());
  }

  @EventListener
  public void handlePlaybackStarted(SongPlaybackStartedEvent event) {
    sendNowPlaying(new NowPlayingMessage(event.songQueueEntry().song()));
    sendPlaybackStatus();
  }

  @EventListener
  public void handlePlaybackPaused(SongPlaybackPausedEvent event) {
    sendPlaybackStatus();
  }

  @EventListener
  public void handleSongPlaybackStoppedEvent(SongPlaybackStoppedEvent event) {
    sendNowPlaying(new NowPlayingMessage(null));
    sendPlaybackStatus();
  }

  @EventListener
  public void handleAllSongsDonePlayingEvent(AllSongsDonePlayingEvent event) {
    sendNowPlaying(new NowPlayingMessage(null));
    sendPlaybackStatus();
  }

  @EventListener
  public void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event) {
    Integer locationId = songLibraryService.getOwnLocationId();
    messagingTemplate.convertAndSend("/topic/genres", songLibraryService.getGenres(locationId));
    messagingTemplate.convertAndSend("/topic/popularity",
        songLibraryService.getMusicByPopularity(locationId));
    sendNowPlaying(new NowPlayingMessage(songPlayerService.getNowPlayingSong(locationId)));
    messagingTemplate.convertAndSend(LocationTopics.queue(locationId),
        songQueueService.getQueuedSongs(locationId));
  }

  private Integer ownLocationId() {
    return songLibraryService.getOwnLocationId();
  }

  private void sendNowPlaying(NowPlayingMessage message) {
    messagingTemplate.convertAndSend(LocationTopics.nowPlaying(ownLocationId()), message);
  }

  private void sendPlaybackStatus() {
    Integer locationId = ownLocationId();
    messagingTemplate.convertAndSend(LocationTopics.playbackStatus(locationId),
        songPlayerService.getPlaybackStatus(locationId));
  }
}
