package com.djt.jukeanator_engine.domain.songplayer.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlaybackStatusDto;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.event.MultipleSongsAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;

/**
 * HTTP client implementation of SongPlayerService.
 *
 * @author tmyers
 */
public class SongPlayerServiceHttpClient implements SongPlayerService {

  private final RestClient restClient;

  public SongPlayerServiceHttpClient(String baseUrl) {

    this.restClient = RestClient.builder().baseUrl(baseUrl).build();
  }

  @Override
  public SongDto getNowPlayingSong() {

    return restClient.get().uri("/api/song-player/nowPlayingSong").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public SongPlaybackStatusDto getPlaybackStatus() {

    return restClient.get().uri("/api/song-player/playbackStatus").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public void playNextTrack() {

    restClient.post().uri("/api/song-player/next").retrieve().toBodilessEntity();
  }

  @Override
  public void pause() {

    restClient.post().uri("/api/song-player/pause").retrieve().toBodilessEntity();
  }

  @Override
  public void stop() {

    restClient.post().uri("/api/song-player/stop").retrieve().toBodilessEntity();
  }

  @Override
  public void lockQueue() {

    restClient.post().uri("/api/song-player/lockQueue").retrieve().toBodilessEntity();
  }

  @Override
  public void unlockQueue() {

    restClient.post().uri("/api/song-player/unlockQueue").retrieve().toBodilessEntity();
  }

  @Override
  public void handleSongAddedToQueueEvent(SongAddedToQueueEvent event) {
    throw new UnsupportedOperationException("This method cannot be invoked by a user");
  }

  @Override
  public void handleMultipleSongsAddedToQueueEvent(MultipleSongsAddedToQueueEvent event) {
    throw new UnsupportedOperationException("This method cannot be invoked by a user");
  }
}
