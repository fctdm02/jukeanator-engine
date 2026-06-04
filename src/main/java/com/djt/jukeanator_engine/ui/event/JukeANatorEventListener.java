package com.djt.jukeanator_engine.ui.event;

import org.springframework.context.event.EventListener;
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
import com.djt.jukeanator_engine.ui.components.JukeANatorFrame;

@Component
public class JukeANatorEventListener {

  private SongLibraryService songLibraryService;
  private SongQueueService songQueueService;
  private SongPlayerService songPlayerService;
  private JukeANatorFrame frame;

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
    frame.setGenres(songLibraryService.getGenres());
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

    frame.setNowPlaying(event.songQueueEntry().getSong());
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
  public void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event) {

    if (frame == null) return;
    
    initializeUi();
  }
  
  private void initializeUi() {

    frame.refreshMusicByPopularityResults();
    frame.setGenres(songLibraryService.getGenres());
    frame.setNowPlaying(songPlayerService.getNowPlayingSong());
    frame.setQueue(songQueueService.getQueuedSongs());
  }  
}