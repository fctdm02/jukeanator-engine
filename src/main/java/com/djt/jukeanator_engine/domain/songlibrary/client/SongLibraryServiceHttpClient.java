package com.djt.jukeanator_engine.domain.songlibrary.client;

import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumMetadataDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AuthenticateForAdminPanelRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.DownloadAlbumCoverArtRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongScanFailedException;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.event.MultipleSongsAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;

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

  // USER ROLE METHODS
  @Override
  public SearchResultDto getMusicByPopularity() {

    return restClient.get().uri("/api/song-library/popular").retrieve().body(SearchResultDto.class);
  }

  @Override
  public SearchResultDto getMusicBySearch(String searchFor) {

    return restClient.get().uri(uriBuilder -> uriBuilder.path("/api/song-library/search")
        .queryParam("searchFor", searchFor).build()).retrieve().body(SearchResultDto.class);
  }

  @Override
  public List<GenreDto> getGenres() {

    return restClient.get().uri("/api/song-library/genres").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public SearchResultDto getGenreMusicByPopularity(String genreName) {

    return restClient.get().uri(uriBuilder -> uriBuilder.path("/api/song-library/genres/popular")
        .queryParam("genreName", genreName).build()).retrieve().body(SearchResultDto.class);
  }

  @Override
  public SearchResultDto getGenreMusicByTitle(String genreName) {

    return restClient.get().uri(uriBuilder -> uriBuilder.path("/api/song-library/genres/title")
        .queryParam("genreName", genreName).build()).retrieve().body(SearchResultDto.class);
  }

  @Override
  public SearchResultDto getGenreMusicByReleaseDate(String genreName) {

    return restClient.get().uri(uriBuilder -> uriBuilder
        .path("/api/song-library/genres/releaseDate").queryParam("genreName", genreName).build())
        .retrieve().body(SearchResultDto.class);
  }

  @Override
  public List<ArtistDto> getArtists() {

    return restClient.get().uri("/api/song-library/artists").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public List<AlbumDto> getAlbums() {

    return restClient.get().uri("/api/song-library/albums").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public List<AlbumDto> getAlbumsForGenre(Integer genreId) {

    return restClient.get().uri("/api/song-library/genres/" + genreId + "/albums").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public ArtistDto getArtistByName(String artistName) {

    return restClient.get().uri(uriBuilder -> uriBuilder.path("/api/song-library/artist")
        .queryParam("artistName", artistName).build()).retrieve().body(ArtistDto.class);
  }

  @Override
  public AlbumDto getAlbumById(Integer albumId) {

    return restClient.get().uri("/api/song-library/albums/" + albumId).retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public SongDto getSongById(Integer albumId, Integer songId) {

    return restClient.get().uri("/api/song-library/songs/" + albumId + "/" + songId).retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  @GetMapping("/songs/random")
  public SongDto getRandomSongFromBackgroundMusicPlaylist() {

    return restClient.get().uri("/api/song-library/songs/random").retrieve().body(SongDto.class);
  }


  // ADMIN ROLE METHODS
  @Override
  public Integer scanFileSystemForSongs() throws SongScanFailedException {

    return restClient.post().uri("/api/song-library/scanNoPath").retrieve().body(Integer.class);
  }

  @Override
  public Integer scanFileSystemForSongs(ScanRequest scanRequest) throws SongScanFailedException {

    return restClient.post().uri("/api/song-library/scan").body(scanRequest).retrieve()
        .body(Integer.class);
  }

  @Override
  public Integer resetSongStatistics() {

    return restClient.post().uri("/api/song-library/resetSongStatistics").retrieve()
        .body(Integer.class);
  }

  @Override
  public Integer restoreSongStatistics(String filename) {

    return restClient.post().uri("/api/song-library/restoreSongStatistics").body(filename)
        .retrieve().body(Integer.class);
  }

  @Override
  public List<AlbumMetadataDto> searchInternetForAlbumMetadata(@RequestParam String artistName,
      @RequestParam String albumName, @RequestParam int limit) {

    return restClient.get().uri("/api/song-library/searchInternetForAlbumMetadata").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public AlbumMetadataDto updateAlbumMetadata(Integer albumId, AlbumMetadataDto albumMetadata) {

    return restClient.post().uri("/api/song-library/albums/" + albumId + "/updateAlbumMetadata")
        .body(albumMetadata).retrieve().body(AlbumMetadataDto.class);
  }

  @Override
  public String downloadAlbumCoverArt(DownloadAlbumCoverArtRequest downloadAlbumCoverArtRequest) {

    return restClient.post().uri("/api/song-library/downloadAlbumCoverArt")
        .body(downloadAlbumCoverArtRequest).retrieve().body(String.class);
  }

  @Override
  public Boolean authenticateForAdminPanel(
      AuthenticateForAdminPanelRequest authenticateForAdminPanelRequest) {

    return restClient.post().uri("/api/song-library/authenticateForAdminPanel")
        .body(authenticateForAdminPanelRequest).retrieve().body(Boolean.class);
  }

  @Override
  public void handleSongAddedToQueueEvent(SongAddedToQueueEvent event) {
    throw new UnsupportedOperationException("This method cannot be invoked by a user");
  }

  @Override
  public void handleMultipleSongsAddedToQueueEvent(MultipleSongsAddedToQueueEvent event) {
    throw new UnsupportedOperationException("This method cannot be invoked by a user");
  }

  @Override
  public RootFolderEntity getSongLibraryRoot() {
    throw new UnsupportedOperationException("This method cannot be invoked by a user");
  }
}
