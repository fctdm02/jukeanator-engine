package com.djt.jukeanator_engine.domain.location.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotAlbumDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotArtistDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotGenreDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotSongDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySyncAckDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;

/**
 * Slave-only. After every scan, builds a flattened metadata snapshot of the local library (see
 * {@link LibrarySnapshotDto} for why this is a plain DTO tree and not the raw
 * {@code RootFolderEntity} object graph) and uploads it to the master, followed by cover art for
 * whichever albums the master reports as missing/stale. Audio files are never uploaded — only the
 * slave plays audio, so only metadata and cover art need to leave the building.
 *
 * @author tmyers
 */
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "slave")
public class LibrarySyncService {

  private static final Logger log = LoggerFactory.getLogger(LibrarySyncService.class);

  private final SongLibraryService songLibraryService;
  private final AppProperties appProperties;
  private final RestClient restClient;

  public LibrarySyncService(SongLibraryService songLibraryService, AppProperties appProperties) {

    this.songLibraryService = songLibraryService;
    this.appProperties = appProperties;
    this.restClient = RestClient.builder().baseUrl(appProperties.getMasterInstanceUrl()).build();
  }

  @EventListener
  public void handleScanFileSystemForSongsEvent(ScanFileSystemForSongsEvent event) {

    try {
      syncLibrary();
    } catch (Throwable t) {
      // The master being unreachable — or literally anything else going wrong here, down to a
      // classpath problem — must never break a slave's own scan/UI. This event listener runs
      // synchronously on the same thread as the scan itself, so an uncaught Throwable here
      // propagates back out of scanFileSystemForSongs() and fails the scan too. This is exactly
      // the "slave keeps working fully offline" requirement, so we catch everything.
      log.warn("Could not sync library to master at " + appProperties.getMasterInstanceUrl(), t);
    }
  }

  private void syncLibrary() {

    LibrarySnapshotDto snapshot = buildSnapshot();

    LibrarySyncAckDto ack = restClient.post()
        .uri("/api/locations/{locationId}/library-sync/metadata", appProperties.getLocationId())
        .header("location-id", appProperties.getLocationId())
        .header("location-api-key", appProperties.getLocationApiKey())
        .contentType(MediaType.APPLICATION_JSON)
        .body(snapshot)
        .retrieve()
        .body(LibrarySyncAckDto.class);

    if (ack == null || ack.sourceAlbumIdsNeedingCoverArt().isEmpty()) {
      return;
    }

    for (AlbumDto album : songLibraryService.getAlbums()) {
      if (ack.sourceAlbumIdsNeedingCoverArt().contains(album.getAlbumId())) {
        uploadCoverArt(album);
      }
    }
  }

  private LibrarySnapshotDto buildSnapshot() {

    List<LibrarySnapshotGenreDto> genres = new ArrayList<>();
    for (GenreDto genre : songLibraryService.getGenres()) {
      genres.add(new LibrarySnapshotGenreDto(genre.getGenreId(), genre.getGenreName()));
    }

    List<LibrarySnapshotArtistDto> artists = new ArrayList<>();
    for (ArtistDto artist : songLibraryService.getArtists()) {
      artists.add(new LibrarySnapshotArtistDto(artist.getArtistId(), artist.getArtistName()));
    }

    List<LibrarySnapshotAlbumDto> albums = new ArrayList<>();
    for (AlbumDto album : songLibraryService.getAlbums()) {

      List<LibrarySnapshotSongDto> songs = new ArrayList<>();
      for (SongDto song : album.getSongs()) {
        songs.add(new LibrarySnapshotSongDto(song.getSongId(), song.getSongName(),
            song.getTrackNumber(), song.getNumPlays()));
      }

      albums.add(new LibrarySnapshotAlbumDto(album.getAlbumId(), album.getAlbumName(),
          album.getArtistId(), album.getArtistName(), album.getGenreId(), album.getGenreName(),
          hashCoverArt(album.getCoverArtPath()), album.getHasExplicit(), album.getRecordLabel(),
          album.getReleaseDate(), album.isCompilation(), songs));
    }

    return new LibrarySnapshotDto(genres, artists, albums);
  }

  private void uploadCoverArt(AlbumDto album) {

    if (album.getCoverArtPath() == null) {
      return;
    }

    byte[] imageBytes;
    try {
      imageBytes = Files.readAllBytes(Path.of(album.getCoverArtPath()));
    } catch (Exception e) {
      log.warn("Could not read cover art at " + album.getCoverArtPath() + " for albumId "
          + album.getAlbumId(), e);
      return;
    }

    restClient.post()
        .uri("/api/locations/{locationId}/library-sync/cover-art/{sourceAlbumId}",
            appProperties.getLocationId(), album.getAlbumId())
        .header("location-id", appProperties.getLocationId())
        .header("location-api-key", appProperties.getLocationApiKey())
        .contentType(MediaType.IMAGE_JPEG)
        .body(imageBytes)
        .retrieve()
        .toBodilessEntity();
  }

  private static String hashCoverArt(String coverArtPath) {

    if (coverArtPath == null) {
      return null;
    }
    try {
      byte[] bytes = Files.readAllBytes(Path.of(coverArtPath));
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (Exception e) {
      return null;
    }
  }
}
