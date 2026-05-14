package com.djt.jukeanator_engine.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.domain.songlibrary.client.SongLibraryServiceHttpClient;
import com.djt.jukeanator_engine.domain.songplayer.client.SongPlayerServiceHttpClient;
import com.djt.jukeanator_engine.domain.songqueue.client.SongQueueServiceHttpClient;
import com.djt.jukeanator_engine.ui.components.JukeANatorFrame;
import com.djt.jukeanator_engine.ui.event.SongPlayerUiEventListener;

@Component
public class JukeANatorUserInterfaceApplication {

  private static final Logger log = LoggerFactory.getLogger(JukeANatorUserInterfaceApplication.class);

  private final SongLibraryServiceHttpClient songLibraryServiceClient;
  private final SongQueueServiceHttpClient songQueueServiceClient;
  private final SongPlayerServiceHttpClient songPlayerServiceClient;
  private final SongPlayerUiEventListener songPlayerUiEventListener;

  private JukeANatorFrame frame;

  public JukeANatorUserInterfaceApplication(
      SongLibraryServiceHttpClient songLibraryServiceClient,
      SongQueueServiceHttpClient songQueueServiceClient,
      SongPlayerServiceHttpClient songPlayerServiceClient,
      SongPlayerUiEventListener songPlayerUiEventListener) {

    this.songLibraryServiceClient = songLibraryServiceClient;
    this.songQueueServiceClient = songQueueServiceClient;
    this.songPlayerServiceClient = songPlayerServiceClient;
    this.songPlayerUiEventListener = songPlayerUiEventListener;
  }

  public void launch() {

    this.frame = new JukeANatorFrame();
    this.songPlayerUiEventListener.setFrame(frame);
    
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