package com.djt.jukeanator_engine.domain.songplayer.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songplayer.dto.SongPlaybackStatusDto;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;
import com.djt.jukeanator_engine.domain.songqueue.event.MultipleSongsAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;

/**
 * HTTP client implementation of SongPlayerService. Unused today (no callers) -- kept as a
 * reference for the shape a remote-backed implementation would take. Every path is now under
 * {@code /api/locations/{locationId}/...}, matching {@code SongPlayerController}'s locationId-scoped
 * mapping.
 *
 * @author tmyers
 */
public class SongPlayerServiceHttpClient implements SongPlayerService {

  private final RestClient restClient;

  public SongPlayerServiceHttpClient(String baseUrl) {

    this.restClient = RestClient.builder().baseUrl(baseUrl).build();
  }

  private String basePath(Integer locationId) {
    return "/api/locations/" + locationId + "/song-player";
  }

  @Override
  public SongDto getNowPlayingSong(Integer locationId) {

    return restClient.get().uri(basePath(locationId) + "/nowPlayingSong").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public SongPlaybackStatusDto getPlaybackStatus(Integer locationId) {

    return restClient.get().uri(basePath(locationId) + "/playbackStatus").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public void playNextTrack(Integer locationId) {

    restClient.post().uri(basePath(locationId) + "/next").retrieve().toBodilessEntity();
  }

  @Override
  public void pause(Integer locationId) {

    restClient.post().uri(basePath(locationId) + "/pause").retrieve().toBodilessEntity();
  }

  @Override
  public void stop(Integer locationId) {

    restClient.post().uri(basePath(locationId) + "/stop").retrieve().toBodilessEntity();
  }

  @Override
  public void lockQueue(Integer locationId) {

    restClient.post().uri(basePath(locationId) + "/lockQueue").retrieve().toBodilessEntity();
  }

  @Override
  public void unlockQueue(Integer locationId) {

    restClient.post().uri(basePath(locationId) + "/unlockQueue").retrieve().toBodilessEntity();
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
