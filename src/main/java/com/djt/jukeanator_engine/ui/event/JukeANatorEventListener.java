package com.djt.jukeanator_engine.ui.event;

import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.domain.songlibrary.client.SongLibraryServiceHttpClient;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songplayer.client.SongPlayerServiceHttpClient;
import com.djt.jukeanator_engine.domain.songplayer.dto.NowPlayingSongDto;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackStartedEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongQueueChangedEvent;
import com.djt.jukeanator_engine.domain.songqueue.client.SongQueueServiceHttpClient;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.event.AddSongToQueueEvent;
import com.djt.jukeanator_engine.ui.components.JukeANatorFrame;

@Component
public class JukeANatorEventListener {

  private SongLibraryServiceHttpClient songLibraryServiceClient;
  private SongQueueServiceHttpClient songQueueServiceClient;
  private SongPlayerServiceHttpClient songPlayerServiceClient;
  private JukeANatorFrame frame;

  public void setFrame(JukeANatorFrame frame) {
    this.frame = frame;
  }
  
  public void setSongLibraryServiceHttpClient(SongLibraryServiceHttpClient songLibraryServiceClient) {
    this.songLibraryServiceClient = songLibraryServiceClient;
  }
  
  public void setSongQueueServiceHttpClient(SongQueueServiceHttpClient songQueueServiceClient) {
    this.songQueueServiceClient = songQueueServiceClient;
  }
  
  public void setSongPlayerServiceHttpClient(SongPlayerServiceHttpClient songPlayerServiceClient) {
    this.songPlayerServiceClient = songPlayerServiceClient;
  }
  
  @EventListener
  public void handleAddSongToQueueEvent(AddSongToQueueEvent event) {
    
    if (frame == null) return;
    
    updateQueue(event.queuedSongs());
  }

  @EventListener
  public void handleSongQueueChangedEvent(SongQueueChangedEvent event) {
    
    if (frame == null) return;
    
    updateQueue(event.queuedSongs());
  }
  
  private void updateQueue(List<SongQueueEntryDto> queue) {
    
    if (frame == null) return;
    
    frame.setQueue(queue);    
  }
  
  @EventListener
  public void handlePlaybackStarted(SongPlaybackStartedEvent event) {

    if (frame == null) return;

    SongQueueEntryDto song = event.song();
    
    String coverArtUrl = song.getCoverArtPath();
    String artistName = song.getArtistName();
    String albumName = song.getAlbumName();
    String songName = song.getSongName();
    
    NowPlayingSongDto dto = new NowPlayingSongDto(coverArtUrl, artistName, albumName, songName);

    frame.setNowPlaying(dto);
  }
  
  @EventListener
  public void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event) {

    if (frame == null) return;
    
    initializeUi();
  }
  
  private void initializeUi() {

    this.frame.setGenres(songLibraryServiceClient.getGenres());
    this.frame.setNowPlaying(songPlayerServiceClient.getNowPlayingSong());
    this.frame.setQueue(songQueueServiceClient.getQueuedSongs());
  }  
}