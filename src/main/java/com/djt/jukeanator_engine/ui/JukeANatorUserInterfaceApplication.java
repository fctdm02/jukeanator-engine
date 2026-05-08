package com.djt.jukeanator_engine.ui;

import javax.swing.JFrame;
import com.djt.jukeanator_engine.domain.songlibrary.client.SongLibraryServiceHttpClient;
import com.djt.jukeanator_engine.domain.songplayer.client.SongPlayerServiceHttpClient;
import com.djt.jukeanator_engine.domain.songqueue.client.SongQueueServiceHttpClient;

public final class JukeANatorUserInterfaceApplication {
  
  private SongLibraryServiceHttpClient songLibraryServiceClient;
  private SongQueueServiceHttpClient songQueueServiceClient;
  private SongPlayerServiceHttpClient songPlayerServiceClient;

  public JukeANatorUserInterfaceApplication(String baseUrl) {
    
    songLibraryServiceClient = new SongLibraryServiceHttpClient(baseUrl); 
    songQueueServiceClient = new SongQueueServiceHttpClient(baseUrl);
    songPlayerServiceClient = new SongPlayerServiceHttpClient(baseUrl);

    JukeANatorFrame frame = new JukeANatorFrame();
    
    frame.setGenres(songLibraryServiceClient.getGenres());
    frame.setNowPlaying(songPlayerServiceClient.getNowPlayingSong());
    
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(400, 300);
    frame.setVisible(true);
  }
}
