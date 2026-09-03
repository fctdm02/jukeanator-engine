package com.djt.jukeanator_engine.domain.songlibrary.controller;

import static java.util.Objects.requireNonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
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
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;

/**
 * Every browse endpoint is scoped by {@code locationId} -- on a standalone/slave instance this is
 * always its own one location; on master it can be any previously-synced location. Admin/scan
 * endpoints stay unscoped (no {@code locationId}), since they're inherently local to whichever
 * instance owns the physical library and are rejected outright on master (see
 * {@code SongLibraryServiceImpl#requireNotMaster()}).
 *
 * @author tmyers
 */
@RestController
@RequestMapping("/api/locations/{locationId}/song-library")
public class SongLibraryController {

  private final SongLibraryService songLibraryService;
  private final LocationService locationService;

  public SongLibraryController(@Qualifier("songLibraryService") SongLibraryService songLibraryService,
      LocationService locationService) {

    requireNonNull(songLibraryService, "songLibraryService cannot be null");
    requireNonNull(locationService, "locationService cannot be null");
    this.songLibraryService = songLibraryService;
    this.locationService = locationService;
  }

  // USER ROLE METHODS

  @GetMapping("/popular")
  public SearchResultDto getMusicByPopularity(@PathVariable Integer locationId) {
    return songLibraryService.getMusicByPopularity(locationId);
  }

  @GetMapping("/search")
  public SearchResultDto getMusicBySearch(@PathVariable Integer locationId,
      @RequestParam String searchFor, @RequestParam(defaultValue = "20") int limit) {
    return songLibraryService.getMusicBySearch(locationId, searchFor, limit);
  }

  @GetMapping("/genres")
  public List<GenreDto> getGenres(@PathVariable Integer locationId) {
    return songLibraryService.getGenres(locationId);
  }

  @GetMapping("/genres/popular")
  public SearchResultDto getGenreMusicByPopularity(@PathVariable Integer locationId,
      @RequestParam String genreName) {
    return songLibraryService.getGenreMusicByPopularity(locationId, genreName);
  }

  @GetMapping("/genres/title")
  public SearchResultDto getGenreMusicByTitle(@PathVariable Integer locationId,
      @RequestParam String genreName) {
    return songLibraryService.getGenreMusicByTitle(locationId, genreName);
  }

  @GetMapping("/artists")
  public List<ArtistDto> getArtists(@PathVariable Integer locationId) {
    return songLibraryService.getArtists(locationId);
  }

  @GetMapping("/artist")
  public ArtistDto getArtistByName(@PathVariable Integer locationId,
      @RequestParam String artistName) {
    return songLibraryService.getArtistByName(locationId, artistName);
  }

  @GetMapping("/albums")
  public List<AlbumDto> getAlbums(@PathVariable Integer locationId) {
    return songLibraryService.getAlbums(locationId);
  }

  @GetMapping("/genres/{genreId}/albums")
  public List<AlbumDto> getAlbumsForGenre(@PathVariable Integer locationId,
      @PathVariable Integer genreId) {
    return songLibraryService.getAlbumsForGenre(locationId, genreId);
  }

  @GetMapping("/albums/{id}")
  public AlbumDto getAlbumById(@PathVariable Integer locationId, @PathVariable Integer id) {
    return songLibraryService.getAlbumById(locationId, id);
  }

  @GetMapping("/artists/{id}")
  public ArtistDto getArtistById(@PathVariable Integer locationId, @PathVariable Integer id) {
    return songLibraryService.getArtistById(locationId, id);
  }

  @GetMapping("/artistByAlbum/{albumId}")
  public ArtistDto getArtistByAlbumId(@PathVariable Integer locationId,
      @PathVariable Integer albumId) {
    return songLibraryService.getArtistByAlbumId(locationId, albumId);
  }

  @GetMapping("/artists/{id}/coverArt")
  public ResponseEntity<Resource> getArtistCoverArt(@PathVariable Integer locationId,
      @PathVariable Integer id) throws EntityDoesNotExistException, IOException {

    ArtistDto artist = songLibraryService.getArtistById(locationId, id);
    if (artist.coverArtPath() == null) {
      throw new EntityDoesNotExistException("No cover art path set for artist: " + id);
    }

    Path coverArtPath = Paths.get(artist.coverArtPath());
    if (!Files.isRegularFile(coverArtPath)) {
      throw new EntityDoesNotExistException(
          "Cover art file does not exist for artist: " + id + " at path: " + coverArtPath);
    }

    return serveCoverArtFile(coverArtPath);
  }

  @GetMapping("/albums/{id}/coverArt")
  public ResponseEntity<Resource> getAlbumCoverArt(@PathVariable Integer locationId,
      @PathVariable Integer id) throws EntityDoesNotExistException, IOException {

    boolean isOwnLocation = Objects.equals(locationId, songLibraryService.getOwnLocationId());

    Path coverArtPath;
    if (isOwnLocation) {

      AlbumDto album = songLibraryService.getAlbumById(locationId, id);
      if (album == null || album.coverArtPath() == null) {
        throw new EntityDoesNotExistException("No cover art path set for album: " + id);
      }
      coverArtPath = Paths.get(album.coverArtPath());

    } else {

      // A remote (synced) location's cover art never lives at the path any locally-loaded
      // AlbumFolderEntity would compute -- it's served from master's own per-location storage
      // root, populated by LocationService as slaves sync.
      coverArtPath = locationService.getCoverArtPath(locationId, id);
      if (coverArtPath == null) {
        throw new EntityDoesNotExistException(
            "No synced cover art for album: " + id + " at locationId: " + locationId);
      }
    }

    if (!Files.isRegularFile(coverArtPath)) {
      throw new EntityDoesNotExistException(
          "Cover art file does not exist for album: " + id + " at path: " + coverArtPath);
    }

    return serveCoverArtFile(coverArtPath);
  }

  private ResponseEntity<Resource> serveCoverArtFile(Path coverArtPath) throws IOException {

    String probed = Files.probeContentType(coverArtPath);
    MediaType contentType =
        probed != null ? MediaType.parseMediaType(probed) : MediaType.APPLICATION_OCTET_STREAM;

    return ResponseEntity.ok()
        .contentType(contentType)
        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
        .body(new FileSystemResource(coverArtPath));
  }

  @GetMapping("/songs/{albumId}/{songId}")
  public SongDto getSongById(@PathVariable Integer locationId, @PathVariable Integer albumId,
      @PathVariable Integer songId) {
    return songLibraryService.getSongById(locationId, albumId, songId);
  }

  // ADMIN ROLE METHODS -- always local to this instance's own location, no locationId path
  // variable would make sense; @RequestMapping's {locationId} is simply ignored/unused here (a
  // caller must still pass its own locationId in the path for URL consistency with the rest of
  // this controller, but it's not otherwise used since admin/scan is only ever valid on the one
  // instance that owns the library).

  @PostMapping("/scanNoPath")
  public Integer scanFileSystemForSongs() throws SongScanFailedException {

    return songLibraryService.scanFileSystemForSongs();
  }

  @PostMapping("/scan")
  public Integer scanFileSystemForSongs(@RequestBody ScanRequest scanRequest)
      throws SongScanFailedException {

    return songLibraryService.scanFileSystemForSongs(scanRequest);
  }

  @PostMapping("/resetSongStatistics")
  public Integer resetSongStatistics() {

    return songLibraryService.resetSongStatistics();
  }

  @PostMapping("/restoreSongStatistics")
  public Integer restoreSongStatistics(@RequestBody String filename) {

    return songLibraryService.restoreSongStatistics(filename);
  }

  @PostMapping("/storeSongLibraryAndStatistics")
  public Integer storeSongLibraryAndStatistics() {

    return songLibraryService.storeSongLibraryAndStatistics();
  }

  @GetMapping("/searchInternetForAlbumMetadata")
  public List<AlbumMetadataDto> searchInternetForAlbumMetadata(@RequestParam String artistName,
      @RequestParam String albumName, int limit) {

    return songLibraryService.searchInternetForAlbumMetadata(artistName, albumName, limit);
  }

  @PostMapping("/albums/{albumId}/updateAlbumMetadata")
  public AlbumMetadataDto updateAlbumMetadata(@PathVariable Integer albumId,
      @RequestBody AlbumMetadataDto albumMetadata) {

    return songLibraryService.updateAlbumMetadata(albumId, albumMetadata);
  }

  @PostMapping("/downloadAlbumCoverArt")
  public String downloadAlbumCoverArt(
      @RequestBody DownloadAlbumCoverArtRequest downloadAlbumCoverArtRequest) {

    return songLibraryService.downloadAlbumCoverArt(downloadAlbumCoverArtRequest);
  }

  @PostMapping("/authenticateForAdminPanel")
  public Boolean authenticateForAdminPanel(
      @RequestBody AuthenticateForAdminPanelRequest authenticateForAdminPanelRequest) {
    return songLibraryService.authenticateForAdminPanel(authenticateForAdminPanelRequest);
  }
}
