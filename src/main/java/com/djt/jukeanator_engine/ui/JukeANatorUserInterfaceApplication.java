package com.djt.jukeanator_engine.ui;

import javax.swing.JFrame;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.djt.jukeanator_engine.domain.songlibrary.client.SongLibraryServiceHttpClient;
import com.djt.jukeanator_engine.domain.songplayer.client.SongPlayerServiceHttpClient;
import com.djt.jukeanator_engine.domain.songqueue.client.SongQueueServiceHttpClient;
import com.djt.jukeanator_engine.domain.songqueue.event.AddSongToQueueEvent;

@Component
public class JukeANatorUserInterfaceApplication {

  private static final Logger log = LoggerFactory.getLogger(JukeANatorUserInterfaceApplication.class);

  private final SongLibraryServiceHttpClient songLibraryServiceClient;
  private final SongQueueServiceHttpClient songQueueServiceClient;
  private final SongPlayerServiceHttpClient songPlayerServiceClient;

  private final JukeANatorFrame jukeANatorFrame;

  public JukeANatorUserInterfaceApplication(
      SongLibraryServiceHttpClient songLibraryServiceClient,
      SongQueueServiceHttpClient songQueueServiceClient,
      SongPlayerServiceHttpClient songPlayerServiceClient) {

    this.songLibraryServiceClient = songLibraryServiceClient;
    this.songQueueServiceClient = songQueueServiceClient;
    this.songPlayerServiceClient = songPlayerServiceClient;

    this.jukeANatorFrame = new JukeANatorFrame();
  }

  public void launch() {

    initialize();

    jukeANatorFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    jukeANatorFrame.setSize(400, 300);
    jukeANatorFrame.setVisible(true);
  }

  @EventListener
  public void handleScanFileSystemForSongsEvent(AddSongToQueueEvent event) {

    log.info("Event: {}", event);

    initialize();
  }

  private void initialize() {

    jukeANatorFrame.setGenres(songLibraryServiceClient.getGenres());

    jukeANatorFrame.setNowPlaying(songPlayerServiceClient.getNowPlayingSong());
  }
}
