package com.djt.jukeanator_engine.ui.event;

import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songplayer.dto.NowPlayingSongDto;
import com.djt.jukeanator_engine.domain.songplayer.event.SongPlaybackStartedEvent;
import com.djt.jukeanator_engine.domain.songplayer.event.SongQueueChangedEvent;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;
import com.djt.jukeanator_engine.ui.JukeANatorFrame;

@Component
public class SongPlayerUiEventListener {

  private JukeANatorFrame frame;

  public void setFrame(JukeANatorFrame frame) {
    this.frame = frame;
  }

  @EventListener
  public void handleSongQueueChangedEvent(SongQueueChangedEvent event) {

    if (frame == null) return;
    
    List<SongQueueEntryDto> queue = event.queue();
    
    frame.setQueue(queue);
  }
  
  @EventListener
  public void handlePlaybackStarted(SongPlaybackStartedEvent event) {

    if (frame == null) return;

    SongQueueEntryEntity songQueueEntry = event.song();
    SongFileEntity song = songQueueEntry.getSong();
    AlbumFolderEntity album = song.getAlbum();
    
    String coverArtUrl = album.getCoverArtPath();
    String artistName = album.getParentArtist().getName();
    String albumName = album.getName();
    String songName = song.getName();
    
    NowPlayingSongDto dto = new NowPlayingSongDto(coverArtUrl, artistName, albumName, songName);

    frame.setNowPlaying(dto);
  }
}