package com.djt.jukeanator_engine.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.domain.songlibrary.client.SongLibraryServiceHttpClient;
import com.djt.jukeanator_engine.domain.songplayer.client.SongPlayerServiceHttpClient;
import com.djt.jukeanator_engine.domain.songqueue.client.SongQueueServiceHttpClient;
import com.djt.jukeanator_engine.ui.components.JukeANatorFrame;
import com.djt.jukeanator_engine.ui.config.JukeANatorUserInterfaceProperties;
import com.djt.jukeanator_engine.ui.event.JukeANatorEventListener;

@Component
public class JukeANatorUserInterfaceApplication {

  private static final Logger log = LoggerFactory.getLogger(JukeANatorUserInterfaceApplication.class);

  private final JukeANatorUserInterfaceProperties jukeANatorUserInterfaceProperties;
  private final SongLibraryServiceHttpClient songLibraryServiceClient;
  private final SongQueueServiceHttpClient songQueueServiceClient;
  private final SongPlayerServiceHttpClient songPlayerServiceClient;
  private final JukeANatorEventListener jukeANatorEventListener;

  private JukeANatorFrame frame;

  public JukeANatorUserInterfaceApplication(
      JukeANatorUserInterfaceProperties jukeANatorUserInterfaceProperties,
      SongLibraryServiceHttpClient songLibraryServiceClient,
      SongQueueServiceHttpClient songQueueServiceClient,
      SongPlayerServiceHttpClient songPlayerServiceClient,
      JukeANatorEventListener jukeANatorEventListener) {

    this.jukeANatorUserInterfaceProperties = jukeANatorUserInterfaceProperties;
    this.songLibraryServiceClient = songLibraryServiceClient;
    this.songQueueServiceClient = songQueueServiceClient;
    this.songPlayerServiceClient = songPlayerServiceClient;
    this.jukeANatorEventListener = jukeANatorEventListener;
  }

  public void launch() {

    this.frame = new JukeANatorFrame(jukeANatorUserInterfaceProperties);
    this.jukeANatorEventListener.setFrame(frame);
    
    initializeUi();

    this.frame.showFullscreen();
    this.frame.setVisible(true);

    log.info("JukeANator UI launched");
  }

  private void initializeUi() {

    this.frame.setGenres(songLibraryServiceClient.getGenres());
    this.frame.setNowPlaying(songPlayerServiceClient.getNowPlayingSong());
    this.frame.setQueue(songQueueServiceClient.getQueuedSongs());
  }
}