package com.djt.jukeanator_engine.domain.songlibrary.controller;

import static java.util.Objects.requireNonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.location.service.LocationServiceRegistry;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;

/**
 * Master-only. Read-only browse/search surface of {@link SongLibraryController}, scoped to a
 * specific location — served from that location's synced snapshot (see
 * {@code SongLibraryServiceLocationProxy}), not a live round-trip to the slave. Admin/scan
 * endpoints are intentionally not mirrored here; they are inherently local to the slave that owns
 * the library.
 *
 * @author tmyers
 */
@RestController
@RequestMapping("/api/locations/{locationId}/song-library")
@ConditionalOnProperty(name = "app.mode", havingValue = "master")
public class LocationScopedSongLibraryController {

  private final LocationServiceRegistry locationServiceRegistry;
  private final LocationService locationService;

  public LocationScopedSongLibraryController(LocationServiceRegistry locationServiceRegistry,
      LocationService locationService) {

    this.locationServiceRegistry = requireNonNull(locationServiceRegistry,
        "locationServiceRegistry cannot be null");
    this.locationService = requireNonNull(locationService, "locationService cannot be null");
  }

  private SongLibraryService libraryService(String locationId) {
    return locationServiceRegistry.resolveSongLibraryService(locationId);
  }

  @GetMapping("/popular")
  public SearchResultDto getMusicByPopularity(@PathVariable String locationId) {
    return libraryService(locationId).getMusicByPopularity();
  }

  @GetMapping("/search")
  public SearchResultDto getMusicBySearch(@PathVariable String locationId,
      @RequestParam String searchFor, @RequestParam(defaultValue = "20") int limit) {
    return libraryService(locationId).getMusicBySearch(searchFor, limit);
  }

  @GetMapping("/genres")
  public List<GenreDto> getGenres(@PathVariable String locationId) {
    return libraryService(locationId).getGenres();
  }

  @GetMapping("/genres/popular")
  public SearchResultDto getGenreMusicByPopularity(@PathVariable String locationId,
      @RequestParam String genreName) {
    return libraryService(locationId).getGenreMusicByPopularity(genreName);
  }

  @GetMapping("/genres/title")
  public SearchResultDto getGenreMusicByTitle(@PathVariable String locationId,
      @RequestParam String genreName) {
    return libraryService(locationId).getGenreMusicByTitle(genreName);
  }

  @GetMapping("/artists")
  public List<ArtistDto> getArtists(@PathVariable String locationId) {
    return libraryService(locationId).getArtists();
  }

  @GetMapping("/artist")
  public ArtistDto getArtistByName(@PathVariable String locationId,
      @RequestParam String artistName) {
    return libraryService(locationId).getArtistByName(artistName);
  }

  @GetMapping("/albums")
  public List<AlbumDto> getAlbums(@PathVariable String locationId) {
    return libraryService(locationId).getAlbums();
  }

  @GetMapping("/genres/{genreId}/albums")
  public List<AlbumDto> getAlbumsForGenre(@PathVariable String locationId,
      @PathVariable Integer genreId) {
    return libraryService(locationId).getAlbumsForGenre(genreId);
  }

  @GetMapping("/albums/{id}")
  public AlbumDto getAlbumById(@PathVariable String locationId, @PathVariable Integer id) {
    return libraryService(locationId).getAlbumById(id);
  }

  @GetMapping("/artists/{id}")
  public ArtistDto getArtistById(@PathVariable String locationId, @PathVariable Integer id) {
    return libraryService(locationId).getArtistById(id);
  }

  @GetMapping("/artistByAlbum/{albumId}")
  public ArtistDto getArtistByAlbumId(@PathVariable String locationId,
      @PathVariable Integer albumId) {
    return libraryService(locationId).getArtistByAlbumId(albumId);
  }

  @GetMapping("/albums/{id}/coverArt")
  public ResponseEntity<Resource> getAlbumCoverArt(@PathVariable String locationId,
      @PathVariable Integer id) throws EntityDoesNotExistException, IOException {

    Path coverArtPath = locationService.getCoverArtPath(locationId, id);
    if (coverArtPath == null) {
      throw new EntityDoesNotExistException(
          "No cover art synced for locationId: " + locationId + ", albumId: " + id);
    }

    String probed = Files.probeContentType(coverArtPath);
    MediaType contentType =
        probed != null ? MediaType.parseMediaType(probed) : MediaType.IMAGE_JPEG;

    return ResponseEntity.ok().contentType(contentType)
        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
        .body(new FileSystemResource(coverArtPath));
  }

  @GetMapping("/songs/{albumId}/{songId}")
  public SongDto getSongById(@PathVariable String locationId, @PathVariable Integer albumId,
      @PathVariable Integer songId) {
    return libraryService(locationId).getSongById(albumId, songId);
  }
}
