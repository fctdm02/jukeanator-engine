package com.djt.jukeanator_engine.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.djt.jukeanator_engine.domain.songlibrary.client.SongLibraryServiceHttpClient;
import com.djt.jukeanator_engine.domain.songplayer.client.SongPlayerServiceHttpClient;
import com.djt.jukeanator_engine.domain.songqueue.client.SongQueueServiceHttpClient;
import com.djt.jukeanator_engine.ui.event.SongPlayerUiEventListener;

@Component
public class JukeANatorUserInterfaceApplication {

  private static final Logger log =
      LoggerFactory.getLogger(JukeANatorUserInterfaceApplication.class);

  private final SongLibraryServiceHttpClient songLibraryServiceClient;
  private final SongQueueServiceHttpClient songQueueServiceClient;
  private final SongPlayerServiceHttpClient songPlayerServiceClient;
  private final SongPlayerUiEventListener songPlayerUiEventListener;

  private final JukeANatorFrame frame;

  public JukeANatorUserInterfaceApplication(
      SongLibraryServiceHttpClient songLibraryServiceClient,
      SongQueueServiceHttpClient songQueueServiceClient,
      SongPlayerServiceHttpClient songPlayerServiceClient,
      SongPlayerUiEventListener songPlayerUiEventListener) {

    this.songLibraryServiceClient = songLibraryServiceClient;
    this.songQueueServiceClient = songQueueServiceClient;
    this.songPlayerServiceClient = songPlayerServiceClient;
    this.songPlayerUiEventListener = songPlayerUiEventListener;

    this.frame = new JukeANatorFrame();

    this.songPlayerUiEventListener.setFrame(frame);
  }

  public void launch() {

    refreshUi();

    frame.showFullscreen();
    frame.setVisible(true);

    log.info("JukeANator UI launched");
  }

  public void refreshUi() {

    frame.setGenres(songLibraryServiceClient.getGenres());
    frame.setNowPlaying(songPlayerServiceClient.getNowPlayingSong());
    frame.setQueue(songQueueServiceClient.getQueuedSongs());
  }
}