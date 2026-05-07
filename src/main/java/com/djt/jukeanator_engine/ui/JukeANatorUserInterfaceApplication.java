package com.djt.jukeanator_engine.ui;

import javax.swing.JFrame;
import com.djt.jukeanator_engine.domain.songlibrary.client.SongLibraryServiceHttpClient;
import com.djt.jukeanator_engine.domain.songqueue.client.SongQueueServiceHttpClient;

public final class JukeANatorUserInterfaceApplication {
  
  private SongLibraryServiceHttpClient songLibraryServiceClient;
  private SongQueueServiceHttpClient songQueueServiceClient;

  public JukeANatorUserInterfaceApplication(String baseUrl) {
    
    songLibraryServiceClient = new SongLibraryServiceHttpClient(baseUrl); 
    songQueueServiceClient = new SongQueueServiceHttpClient(baseUrl);

    JukeANatorFrame frame = new JukeANatorFrame();
    
    frame.setGenres(songLibraryServiceClient.getGenres());
    frame.setNowPlaying(songQueueServiceClient.getFirstEntryInSongQueue());
    
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(400, 300);
    frame.setVisible(true);
  }
}
