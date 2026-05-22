package com.djt.jukeanator_engine.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.ui.components.JukeANatorFrame;
import com.djt.jukeanator_engine.ui.config.JukeANatorUserInterfaceProperties;
import com.djt.jukeanator_engine.ui.event.JukeANatorEventListener;

@Component
public class JukeANatorUserInterfaceApplication {

  private static final Logger log = LoggerFactory.getLogger(JukeANatorUserInterfaceApplication.class);

  private final JukeANatorUserInterfaceProperties jukeANatorUserInterfaceProperties;
  private final SongLibraryService songLibraryService;
  private final SongQueueService songQueueService;
  private final SongPlayerService songPlayerService;
  private final JukeANatorEventListener jukeANatorEventListener;

  private JukeANatorFrame frame;

  public JukeANatorUserInterfaceApplication(
      JukeANatorUserInterfaceProperties jukeANatorUserInterfaceProperties,
      SongLibraryService songLibraryService,
      SongQueueService songQueueService,
      SongPlayerService songPlayerService,
      JukeANatorEventListener jukeANatorEventListener) {

    this.jukeANatorUserInterfaceProperties = jukeANatorUserInterfaceProperties;
    this.songLibraryService = songLibraryService;
    this.songQueueService = songQueueService;
    this.songPlayerService = songPlayerService;
    this.jukeANatorEventListener = jukeANatorEventListener;
  }

  public void launch() {

    this.frame = new JukeANatorFrame(
        jukeANatorUserInterfaceProperties,
        songLibraryService,
        songQueueService,
        songPlayerService);
    
    this.jukeANatorEventListener.setFrame(frame);
    this.jukeANatorEventListener.setSongLibraryService(songLibraryService);
    this.jukeANatorEventListener.setSongQueueService(songQueueService);
    this.jukeANatorEventListener.setSongPlayerService(songPlayerService);
    
    initializeUi();

    this.frame.showFullscreen();
    this.frame.setVisible(true);

    log.info("JukeANator UI launched");
  }

  private void initializeUi() {

    this.frame.setGenres(songLibraryService.getGenres());
    this.frame.setNowPlaying(songPlayerService.getNowPlayingSong());
    this.frame.setQueue(songQueueService.getQueuedSongs());
  }
}