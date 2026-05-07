package com.djt.jukeanator_engine.domain.songqueue.client;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
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
    this.restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build();
  }

  @Override
  public List<SongQueueEntryDto> getQueuedSongs() {

    return restClient.get()
        .uri("/api/song-queue/queuedSongs")
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public Integer addSongToQueue(
      Integer albumId,
      Integer songId,
      Integer priority) {

    AddSongToQueueRequest request = new AddSongToQueueRequest();
    request.setAlbumId(albumId);
    request.setSongId(songId);
    request.setPriority(priority);

    return restClient.post()
        .uri("/api/song-queue/addSong")
        .body(request)
        .retrieve()
        .body(Integer.class);
  }

  @Override
  public SongQueueEntryDto getFirstEntryInSongQueue() {

    return restClient.get()
        .uri("/api/song-queue/first")
        .retrieve()
        .body(SongQueueEntryDto.class);
  }
}