package com.djt.jukeanator_engine.domain.songqueue.client;

import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.LoadPlaylistIntoQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

/**
 * HTTP client implementation of SongQueueService. Unused today (no callers) -- kept as a
 * reference for the shape a remote-backed implementation would take. Every path is now under
 * {@code /api/locations/{locationId}/...}, matching {@code SongQueueController}'s locationId-scoped
 * mapping.
 *
 * @author tmyers
 */
public class SongQueueServiceHttpClient implements SongQueueService {

  private final RestClient restClient;

  public SongQueueServiceHttpClient(String baseUrl) {
    this.restClient = RestClient.builder().baseUrl(baseUrl).build();
  }

  private String basePath(Integer locationId) {
    return "/api/locations/" + locationId + "/song-queue";
  }

  /**
   * NOTE: System method, not to be invoked on behalf of a user
   *
   * @return
   */
  @Override
  public SongQueueEntryDto dequeueNextSong() {
    throw new UnsupportedOperationException(
        "System method, not to be invoked on behalf of a user!");
  }

  /**
   * NOTE: System method, not to be invoked on behalf of a user
   *
   * @return
   */
  @Override
  public boolean isQueueEmpty() {
    throw new UnsupportedOperationException(
        "System method, not to be invoked on behalf of a user!");
  }

  /**
   * NOTE: System method, not to be invoked on behalf of a user
   *
   * @return
   */
  @Override
  public boolean isBackgroundMusicEnabled() {
    throw new UnsupportedOperationException(
        "System method, not to be invoked on behalf of a user!");
  }

  @Override
  public Integer getHighestPriority(Integer locationId) {

    return restClient.post().uri(basePath(locationId) + "/highestPriority").retrieve()
        .body(Integer.class);
  }

  @Override
  public List<SongQueueEntryDto> getQueuedSongs(Integer locationId) {

    return restClient.get().uri(basePath(locationId) + "/queuedSongs").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public String isSongEligibleForQueue(Integer locationId, Integer albumId, Integer songId,
      Integer priority) {

    return restClient.get()
        .uri(uriBuilder -> uriBuilder.path(basePath(locationId) + "/isSongEligibleForQueue")
            .queryParam("albumId", albumId).queryParam("songId", songId)
            .queryParam("priority", priority).build())
        .retrieve().body(String.class);
  }

  @Override
  public SongQueueEntryDto addSongToQueue(Integer locationId,
      AddSongToQueueRequest addSongToQueueRequest) {

    return restClient.post().uri(basePath(locationId) + "/addSong").body(addSongToQueueRequest)
        .retrieve().body(SongQueueEntryDto.class);
  }

  @Override
  public List<SongQueueEntryDto> addAlbumToQueue(Integer locationId,
      AddAlbumToQueueRequest addAlbumToQueueRequest) {

    return restClient.post().uri(basePath(locationId) + "/addAlbum").body(addAlbumToQueueRequest)
        .retrieve().body(new ParameterizedTypeReference<List<SongQueueEntryDto>>() {});
  }

  @Override
  public List<SongQueueEntryDto> addMultipleSongsToQueue(Integer locationId,
      AddMultipleSongsToQueueRequest addMultipleSongsToQueueRequest) {

    return restClient.post().uri(basePath(locationId) + "/addMultipleSongs")
        .body(addMultipleSongsToQueueRequest).retrieve()
        .body(new ParameterizedTypeReference<List<SongQueueEntryDto>>() {});
  }

  @Override
  public Integer flushQueue(Integer locationId) {

    return restClient.post().uri(basePath(locationId) + "/flushQueue").retrieve()
        .body(Integer.class);
  }

  @Override
  public Integer randomizeQueue(Integer locationId) {

    return restClient.post().uri(basePath(locationId) + "/randomizeQueue").retrieve()
        .body(Integer.class);
  }

  @Override
  public Integer moveSongUpInQueue(Integer locationId,
      ChangeSongQueueRequest changeSongQueueRequest) {

    return restClient.post().uri(basePath(locationId) + "/moveSongUpInQueue")
        .body(changeSongQueueRequest).retrieve().body(Integer.class);
  }

  @Override
  public Integer moveSongDownInQueue(Integer locationId,
      ChangeSongQueueRequest changeSongQueueRequest) {

    return restClient.post().uri(basePath(locationId) + "/moveSongDownInQueue")
        .body(changeSongQueueRequest).retrieve().body(Integer.class);
  }

  @Override
  public Integer removeSongDownFromQueue(Integer locationId,
      ChangeSongQueueRequest changeSongQueueRequest) {

    return restClient.post().uri(basePath(locationId) + "/removeSongDownFromQueue")
        .body(changeSongQueueRequest).retrieve().body(Integer.class);
  }

  @Override
  public Integer saveQueueAsPlaylist(Integer locationId, String filename) {

    return restClient.post().uri(basePath(locationId) + "/saveQueueAsPlaylist").body(filename)
        .retrieve().body(Integer.class);
  }

  @Override
  public Integer loadPlaylistIntoQueue(Integer locationId,
      LoadPlaylistIntoQueueRequest loadPlaylistIntoQueueRequest) {

    return restClient.post().uri(basePath(locationId) + "/loadPlaylistIntoQueue")
        .body(loadPlaylistIntoQueueRequest).retrieve().body(Integer.class);
  }

  @Override
  public void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event) {
    throw new UnsupportedOperationException("This method cannot be invoked by a user");
  }

  /**
   * NOTE: System method, not to be invoked on behalf of a user
   *
   * @return
   */
  @Override
  public Integer storeSongQueue() {
    throw new UnsupportedOperationException(
        "System method, not to be invoked on behalf of a user!");
  }

  @Override
  public Integer getOwnLocationId() {
    throw new UnsupportedOperationException("This method cannot be invoked by a user");
  }
}
