package com.djt.jukeanator_engine.domain.songqueue.client;

import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddAlbumToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.ChangeSongQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongQueueEntryDto;
import com.djt.jukeanator_engine.domain.songqueue.service.SongQueueService;

/**
 * HTTP client implementation of SongQueueService.
 * 
 * @author tmyers
 */
public class SongQueueServiceHttpClient implements SongQueueService {

  private final RestClient restClient;

  public SongQueueServiceHttpClient(String baseUrl) {
    this.restClient = RestClient.builder().baseUrl(baseUrl).build();
  }

  @Override
  public SongQueueEntryDto dequeueNextSong() {

    return restClient.get().uri("/api/song-queue/dequeueNextSong").retrieve()
        .body(SongQueueEntryDto.class);
  }

  @Override
  public Integer getHighestPriority() {

    return restClient.post().uri("/api/song-queue/highestPriority").retrieve().body(Integer.class);
  }

  @Override
  public List<SongQueueEntryDto> getQueuedSongs() {

    return restClient.get().uri("/api/song-queue/queuedSongs").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public SongQueueEntryDto addSongToQueue(AddSongToQueueRequest addSongToQueueRequest) {

    return restClient.post().uri("/api/song-queue/addSong").body(addSongToQueueRequest).retrieve()
        .body(SongQueueEntryDto.class);
  }

  @Override
  public List<SongQueueEntryDto> addAlbumToQueue(AddAlbumToQueueRequest addAlbumToQueueRequest) {

    return restClient.post().uri("/api/song-queue/addAlbum").body(addAlbumToQueueRequest).retrieve()
        .body(new ParameterizedTypeReference<List<SongQueueEntryDto>>() {});
  }

  @Override
  public List<SongQueueEntryDto> addMultipleSongsToQueue(
      AddMultipleSongsToQueueRequest addMultipleSongsToQueueRequest) {

    return restClient.post().uri("/api/song-queue/addMultipleSongs")
        .body(addMultipleSongsToQueueRequest).retrieve()
        .body(new ParameterizedTypeReference<List<SongQueueEntryDto>>() {});
  }

  @Override
  public Integer flushQueue() {

    return restClient.post().uri("/api/song-queue/flushQueue").retrieve().body(Integer.class);
  }

  @Override
  public Integer randomizeQueue() {

    return restClient.post().uri("/api/song-queue/randomizeQueue").retrieve().body(Integer.class);
  }

  @Override
  public Integer moveSongUpInQueue(ChangeSongQueueRequest changeSongQueueRequest) {

    return restClient.post().uri("/api/song-queue/moveSongUpInQueue").body(changeSongQueueRequest)
        .retrieve().body(Integer.class);
  }

  @Override
  public Integer moveSongDownInQueue(ChangeSongQueueRequest changeSongQueueRequest) {

    return restClient.post().uri("/api/song-queue/moveSongDownInQueue").body(changeSongQueueRequest)
        .retrieve().body(Integer.class);
  }

  @Override
  public Integer removeSongDownFromQueue(ChangeSongQueueRequest changeSongQueueRequest) {

    return restClient.post().uri("/api/song-queue/removeSongDownFromQueue")
        .body(changeSongQueueRequest).retrieve().body(Integer.class);
  }

  @Override
  public Integer saveQueueAsPlaylist(String filename) {

    return restClient.post().uri("/api/song-queue/saveQueueAsPlaylist").body(filename).retrieve()
        .body(Integer.class);
  }

  @Override
  public Integer loadPlaylistIntoQueue(String filename) {

    return restClient.post().uri("/api/song-queue/loadPlaylistIntoQueue").body(filename).retrieve()
        .body(Integer.class);
  }
}
