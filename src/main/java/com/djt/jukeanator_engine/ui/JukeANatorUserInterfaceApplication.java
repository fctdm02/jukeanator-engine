package com.djt.jukeanator_engine.ui;

import javax.swing.JFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import com.djt.jukeanator_engine.domain.songlibrary.client.SongLibraryServiceHttpClient;
import com.djt.jukeanator_engine.domain.songplayer.client.SongPlayerServiceHttpClient;
import com.djt.jukeanator_engine.domain.songqueue.client.SongQueueServiceHttpClient;
import com.djt.jukeanator_engine.domain.songqueue.event.AddSongToQueueEvent;

public class JukeANatorUserInterfaceApplication {

  private static final Logger log = LoggerFactory.getLogger(JukeANatorUserInterfaceApplication.class);
  
  private SongLibraryServiceHttpClient songLibraryServiceClient;
  private SongQueueServiceHttpClient songQueueServiceClient;
  private SongPlayerServiceHttpClient songPlayerServiceClient;
  
  private JukeANatorFrame jukeANatorFrame;

  public JukeANatorUserInterfaceApplication(String baseUrl) {
    
    songLibraryServiceClient = new SongLibraryServiceHttpClient(baseUrl); 
    songQueueServiceClient = new SongQueueServiceHttpClient(baseUrl);
    songPlayerServiceClient = new SongPlayerServiceHttpClient(baseUrl);

    jukeANatorFrame = new JukeANatorFrame();

    initialize();
    
    jukeANatorFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    jukeANatorFrame.setSize(400, 300);
    jukeANatorFrame.setVisible(true);
  }
  
  @EventListener
  public void handleScanFileSystemForSongsEvent(AddSongToQueueEvent event) {

    log.info("""
        Received {}:
        songId={}
        albumId={}
        priorityId={}
        """,
        event.getClass().getSimpleName(),
        event.albumId(),
        event.songId(),
        event.priority()
    );
    
    initialize();
  } 
  
  private void initialize() {
    
    jukeANatorFrame.setGenres(songLibraryServiceClient.getGenres());
    jukeANatorFrame.setNowPlaying(songPlayerServiceClient.getNowPlayingSong());    
  }  
}
