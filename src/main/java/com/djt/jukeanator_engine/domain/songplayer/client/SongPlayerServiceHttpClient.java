package com.djt.jukeanator_engine.domain.songplayer.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import com.djt.jukeanator_engine.domain.songplayer.dto.NowPlayingSongDto;
import com.djt.jukeanator_engine.domain.songplayer.service.SongPlayerService;

/**
 * HTTP client implementation of SongPlayerService.
 * 
 * @author tmyers
 */
public class SongPlayerServiceHttpClient implements SongPlayerService {

  private final RestClient restClient;

  public SongPlayerServiceHttpClient(String baseUrl) {
    this.restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build();
  }

  @Override
  public NowPlayingSongDto getNowPlayingSong() {

    return restClient.get()
        .uri("/api/song-player/nowPlayingSong")
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }
}