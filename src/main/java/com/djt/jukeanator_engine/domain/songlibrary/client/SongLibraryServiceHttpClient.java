package com.djt.jukeanator_engine.domain.songlibrary.client;

import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongScanFailedException;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;

/**
 * HTTP client implementation of SongLibraryService.
 * 
 * @author tmyers
 */
public class SongLibraryServiceHttpClient implements SongLibraryService {

  private final RestClient restClient;

  public SongLibraryServiceHttpClient(String baseUrl) {
    this.restClient = RestClient.builder().baseUrl(baseUrl).build();
  }

  @Override
  public List<String> getGenres() {
    return restClient.get().uri("/api/song-library/genres").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public List<String> getArtists() {
    return restClient.get().uri("/api/song-library/artists").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public List<AlbumDto> getAlbums() {
    return restClient.get().uri("/api/song-library/albums").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public Integer scanFileSystemForSongs(ScanRequest scanRequest) throws SongScanFailedException {

    return restClient.post().uri("/api/song-library/scan").body(scanRequest).retrieve()
        .body(Integer.class);
  }
}
