package com.djt.jukeanator_engine.domain.songlibrary.client;

import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
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
 * HTTP client implementation of SongLibraryService. Unused today (no callers) -- kept as a
 * reference for the shape a remote-backed implementation would take, same role as {@code
 * SongQueueServiceHttpClient}. Every path is now under {@code /api/locations/{locationId}/...},
 * matching {@code SongLibraryController}'s locationId-scoped mapping.
 *
 * @author tmyers
 */
public class SongLibraryServiceHttpClient implements SongLibraryService {

  private final RestClient restClient;

  public SongLibraryServiceHttpClient(String baseUrl) {
    this.restClient = RestClient.builder().baseUrl(baseUrl).build();
  }

  private String basePath(Integer locationId) {
    return "/api/locations/" + locationId + "/song-library";
  }

  // USER ROLE METHODS
  @Override
  public SearchResultDto getMusicByPopularity(Integer locationId) {

    return restClient.get().uri(basePath(locationId) + "/popular").retrieve()
        .body(SearchResultDto.class);
  }

  @Override
  public SearchResultDto getMusicBySearch(Integer locationId, String searchFor) {
    return getMusicBySearch(locationId, searchFor, 20);
  }

  @Override
  public SearchResultDto getMusicBySearch(Integer locationId, String searchFor, int limit) {

    return restClient.get().uri(uriBuilder -> uriBuilder.path(basePath(locationId) + "/search")
        .queryParam("searchFor", searchFor).queryParam("limit", limit).build()).retrieve()
        .body(SearchResultDto.class);
  }

  @Override
  public List<GenreDto> getGenres(Integer locationId) {

    return restClient.get().uri(basePath(locationId) + "/genres").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public SearchResultDto getGenreMusicByPopularity(Integer locationId, String genreName) {

    return restClient.get()
        .uri(uriBuilder -> uriBuilder.path(basePath(locationId) + "/genres/popular")
            .queryParam("genreName", genreName).build())
        .retrieve().body(SearchResultDto.class);
  }

  @Override
  public SearchResultDto getGenreMusicByTitle(Integer locationId, String genreName) {

    return restClient.get()
        .uri(uriBuilder -> uriBuilder.path(basePath(locationId) + "/genres/title")
            .queryParam("genreName", genreName).build())
        .retrieve().body(SearchResultDto.class);
  }

  @Override
  public List<ArtistDto> getArtists(Integer locationId) {

    return restClient.get().uri(basePath(locationId) + "/artists").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public List<AlbumDto> getAlbums(Integer locationId) {

    return restClient.get().uri(basePath(locationId) + "/albums").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public List<AlbumDto> getAlbumsForGenre(Integer locationId, Integer genreId) {

    return restClient.get().uri(basePath(locationId) + "/genres/" + genreId + "/albums").retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public ArtistDto getArtistByName(Integer locationId, String artistName) {

    return restClient.get().uri(uriBuilder -> uriBuilder.path(basePath(locationId) + "/artist")
        .queryParam("artistName", artistName).build()).retrieve().body(ArtistDto.class);
  }

  @Override
  public ArtistDto getArtistById(Integer locationId, Integer artistId) {

    return restClient.get().uri(basePath(locationId) + "/artists/" + artistId).retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public ArtistDto getArtistByAlbumId(Integer locationId, Integer albumId) {

    return restClient.get().uri(basePath(locationId) + "/artistByAlbum/" + albumId).retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public AlbumDto getAlbumById(Integer locationId, Integer albumId) {

    return restClient.get().uri(basePath(locationId) + "/albums/" + albumId).retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public SongDto getSongById(Integer locationId, Integer albumId, Integer songId) {

    return restClient.get().uri(basePath(locationId) + "/songs/" + albumId + "/" + songId)
        .retrieve().body(new ParameterizedTypeReference<>() {});
  }

  // ADMIN ROLE METHODS -- always local to whichever instance owns the library; this client only
  // ever targets its own instance for these, so locationId isn't threaded through them.
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
  public Integer storeSongLibraryAndStatistics() {

    return restClient.post().uri("/api/song-library/storeSongLibraryAndStatistics")
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
  public RootFolderEntity getSongLibraryRoot(Integer locationId) {
    throw new UnsupportedOperationException("This method cannot be invoked by a user");
  }

  @Override
  public Integer getOwnLocationId() {
    throw new UnsupportedOperationException("This method cannot be invoked by a user");
  }

  @Override
  public LocationEntity getOwnLocation() {
    throw new UnsupportedOperationException("This method cannot be invoked by a user");
  }

  @Override
  public void reinitializeOwnLocation() {
    throw new UnsupportedOperationException("This method cannot be invoked by a user");
  }

  @Override
  public Boolean isLibraryLoadFailedAtStartup() {
    throw new UnsupportedOperationException("This method cannot be invoked by a user");
  }
}
