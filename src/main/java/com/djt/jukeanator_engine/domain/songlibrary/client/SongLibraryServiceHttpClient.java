package com.djt.jukeanator_engine.domain.songlibrary.client;

import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumMetadataSearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
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
  public SearchResultDto getMusicByPopularity() {

    return restClient.get()
        .uri("/api/song-library/popular")
        .retrieve()
        .body(SearchResultDto.class);
  }
  
  @Override
  public SearchResultDto getMusicBySearch(String searchFor) {

    return restClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/api/song-library/search")
            .queryParam("searchFor", searchFor)
            .build())
        .retrieve()
        .body(SearchResultDto.class);
  }
  
  @Override
  public List<GenreDto> getGenres() {
    
    return restClient.get()
        .uri("/api/song-library/genres").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public SearchResultDto getGenreMusicByPopularity(String genreName) {

    return restClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/api/song-library/genres/popular")
            .queryParam("genreName", genreName)
            .build())
        .retrieve()
        .body(SearchResultDto.class);
  }

  @Override
  public SearchResultDto getGenreMusicByTitle(String genreName) {

    return restClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/api/song-library/genres/title")
            .queryParam("genreName", genreName)
            .build())
        .retrieve()
        .body(SearchResultDto.class);
  }

  @Override
  public SearchResultDto getGenreMusicByReleaseDate(String genreName) {

    return restClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/api/song-library/genres/releaseDate")
            .queryParam("genreName", genreName)
            .build())
        .retrieve()
        .body(SearchResultDto.class);
  }
  
  @Override
  public List<ArtistDto> getArtists() {
    
    return restClient.get(
        ).uri("/api/song-library/artists").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public List<AlbumDto> getAlbums() {
    
    return restClient.get()
        .uri("/api/song-library/albums").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public List<AlbumDto> getAlbumsForGenre(Integer genreId) {

    return restClient.get()
        .uri("/api/song-library/genres/" + genreId + "/albums").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }
  
  @Override
  public ArtistDto getArtistById(Integer artistId) {
    
    return restClient.get()
        .uri("/api/song-library/artists/" + artistId).retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public AlbumDto getAlbumById(Integer albumId) {
    
    return restClient.get()
        .uri("/api/song-library/albums/" + albumId).retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public SongDto getSongById(Integer albumId, Integer songId) {
    
    return restClient.get()
        .uri("/api/song-library/songs/" + albumId + "/" + songId).retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public Integer scanFileSystemForSongs() throws SongScanFailedException {

    return restClient.post()
        .uri("/api/song-library/scanNoPath").retrieve()
        .body(Integer.class);
  }
  
  @Override
  public Integer scanFileSystemForSongs(ScanRequest scanRequest) throws SongScanFailedException {

    return restClient.post()
        .uri("/api/song-library/scan").body(scanRequest).retrieve()
        .body(Integer.class);
  }
  
  @Override
  public Integer resetSongStatistics() {

    return restClient.post()
        .uri("/api/song-library/resetSongStatistics").retrieve()
        .body(Integer.class);
  }
  
  @Override
  public List<AlbumMetadataSearchResultDto> searchInternetForAlbumMetadata(@RequestParam String artistName, @RequestParam String albumName) {

    return restClient.get()
        .uri("/api/song-library/searchInternetForAlbumMetadata").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }    
}
