package com.djt.jukeanator_engine.ui;

import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;
import com.djt.jukeanator_engine.ui.components.JukeANatorFrame;
import com.djt.jukeanator_engine.ui.config.JukeANatorUserInterfaceProperties;
import com.djt.jukeanator_engine.ui.event.JukeANatorEventListener;
import com.djt.jukeanator_engine.ui.security.SwingSecurityUtil;
import java.util.List;

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

    // Show the frame immediately with loading placeholders so the UI is responsive
    // at once. All service calls (which may hit the database) are dispatched to a
    // background thread and pushed back to the EDT only when they complete.
    this.frame.showFullscreen();
    this.frame.setVisible(true);

    log.info("JukeANator UI launched — fetching library data in background");

    SwingSecurityUtil.runAsync(() -> {

      List<AlbumDto>   albums  = songLibraryService.getAlbums();
      SearchResultDto  popular = songLibraryService.getMusicByPopularity();
      var              genres  = songLibraryService.getGenres();
      var              playing = songPlayerService.getNowPlayingSong();
      var              queue   = songQueueService.getQueuedSongs();

      SwingUtilities.invokeLater(() -> {
        frame.setAlbums(albums, popular);
        frame.setGenres(genres);
        frame.setNowPlaying(playing);
        frame.setQueue(queue);
        log.info("JukeANator UI data load complete");
      });
    });
  }
}