package com.djt.jukeanator_engine.ui.event;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.domain.songlibrary.event.AlbumMetadataChangedEvent;
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
import com.djt.jukeanator_engine.ui.components.JukeANatorFrame;

@Component
public class JukeANatorEventListener {

  private SongLibraryService songLibraryService;
  private SongQueueService songQueueService;
  private SongPlayerService songPlayerService;
  private JukeANatorFrame frame;
  private final AtomicInteger scanReloadCount = new AtomicInteger();

  public void setFrame(JukeANatorFrame frame) {
    this.frame = frame;
  }
  
  public void setSongLibraryService(SongLibraryService songLibraryService) {
    this.songLibraryService = songLibraryService;
  }
  
  public void setSongQueueService(SongQueueService songQueueService) {
    this.songQueueService = songQueueService;
  }
  
  public void setSongPlayerService(SongPlayerService songPlayerService) {
    this.songPlayerService = songPlayerService;
  }

  @EventListener
  public void handleSongAddedToQueueEvent(SongStatisticsChangedEvent event) {

    if (frame == null) return;
    
    frame.refreshMusicByPopularityResults();
    frame.setGenres(songLibraryService.getGenres(songLibraryService.getOwnLocationId()));
  }

  @EventListener
  public void handleMultipleSongsAddedToQueueEvent(MultipleSongsAddedToQueueEvent event) {

    if (frame == null) return;
    
    frame.refreshMusicByPopularityResults();
  }
  
  @EventListener
  public void handleSongQueueChangedEvent(SongQueueChangedEvent event) {
    
    if (frame == null) return;
    
    frame.setQueue(event.queuedSongs());
  }
  
  @EventListener
  public void handlePlaybackStarted(SongPlaybackStartedEvent event) {

    if (frame == null) return;

    frame.setNowPlaying(event.songQueueEntry().song());
  }

  @EventListener
  public void handlePlaybackPaused(SongPlaybackPausedEvent event) {

    if (frame == null) return;

    frame.toggleMusicPlayStateIcon();
  }
  
  @EventListener
  public void handleSongPlaybackStoppedEvent(SongPlaybackStoppedEvent event) {

    if (frame == null) return;

    frame.setNowPlaying(null);
  }

  @EventListener
  public void handleAllSongsDonePlayingEvent(AllSongsDonePlayingEvent event) {

    if (frame == null) return;

    frame.setNowPlaying(null);
  }
  
  @EventListener
  public void handleAlbumMetadataChangedEvent(AlbumMetadataChangedEvent event) {

    if (frame == null) return;

    // HomePanel holds the album list it loaded at startup; reload it so the edited album's
    // record label / release date / explicit flag show up without a restart.
    Integer locationId = songLibraryService.getOwnLocationId();
    frame.setAlbums(songLibraryService.getAlbums(locationId),
        songLibraryService.getArtists(locationId).size());
  }

  @EventListener
  public void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event) {

    if (frame == null) return;

    // Counted before fetching, so a startup data load that began earlier can tell its data is stale
    scanReloadCount.incrementAndGet();
    initializeUi();
  }

  /**
   * How many times a library scan has reloaded the UI. The startup data load in
   * {@code JukeANatorUserInterfaceApplication} runs concurrently with a first-time scan; it reads
   * this before fetching and skips pushing its (possibly pre-scan, empty) data if a scan reload
   * happened in the meantime, so it cannot overwrite the scanned library on the EDT.
   */
  public int getScanReloadCount() {
    return scanReloadCount.get();
  }
  
  private void initializeUi() {

    Integer locationId = songLibraryService.getOwnLocationId();
    frame.setAlbums(songLibraryService.getAlbums(locationId),
        songLibraryService.getArtists(locationId).size());
    frame.refreshMusicByPopularityResults();
    frame.setGenres(songLibraryService.getGenres(locationId));
    frame.setNowPlaying(songPlayerService.getNowPlayingSong(locationId));
    frame.setQueue(songQueueService.getQueuedSongs(locationId));
  }
}