package com.djt.jukeanator_engine.domain.songlibrary.controller;

import static java.util.Objects.requireNonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
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
 * @author tmyers
 */
@RestController
@RequestMapping("/api/song-library")
public class SongLibraryController {

  private final SongLibraryService songLibraryService;

  public SongLibraryController(
      @Qualifier("songLibraryService") SongLibraryService songLibraryService) {

    requireNonNull(songLibraryService, "songLibraryService cannot be null");
    this.songLibraryService = songLibraryService;
  }

  // USER ROLE METHODS

  @GetMapping("/popular")
  public SearchResultDto getMusicByPopularity() {
    return songLibraryService.getMusicByPopularity();
  }


  @GetMapping("/search")
  public SearchResultDto getMusicBySearch(@RequestParam String searchFor,
      @RequestParam(defaultValue = "20") int limit) {
    return songLibraryService.getMusicBySearch(searchFor, limit);
  }


  @GetMapping("/genres")
  public List<GenreDto> getGenres() {
    return songLibraryService.getGenres();
  }


  @GetMapping("/genres/popular")
  public SearchResultDto getGenreMusicByPopularity(@RequestParam String genreName) {
    return songLibraryService.getGenreMusicByPopularity(genreName);
  }


  @GetMapping("/genres/title")
  public SearchResultDto getGenreMusicByTitle(@RequestParam String genreName) {
    return songLibraryService.getGenreMusicByTitle(genreName);
  }


  @GetMapping("/artists")
  public List<ArtistDto> getArtists() {
    return songLibraryService.getArtists();
  }


  @GetMapping("/artist")
  public ArtistDto getArtistByName(@RequestParam String artistName) {
    return songLibraryService.getArtistByName(artistName);
  }


  @GetMapping("/albums")
  public List<AlbumDto> getAlbums() {
    return songLibraryService.getAlbums();
  }


  @GetMapping("/genres/{genreId}/albums")
  public List<AlbumDto> getAlbumsForGenre(@PathVariable Integer genreId) {
    return songLibraryService.getAlbumsForGenre(genreId);
  }


  @GetMapping("/albums/{id}")
  public AlbumDto getAlbumById(@PathVariable Integer id) {
    return songLibraryService.getAlbumById(id);
  }

  @GetMapping("/artists/{id}")
  public ArtistDto getArtistById(@PathVariable Integer id) {
    return songLibraryService.getArtistById(id);
  }


  @GetMapping("/artists/{id}/coverArt")
  public ResponseEntity<Resource> getArtistCoverArt(@PathVariable Integer id)
      throws EntityDoesNotExistException, IOException {

    ArtistDto artist = songLibraryService.getArtistById(id);
    if (artist.getCoverArtPath() == null) {
      throw new EntityDoesNotExistException("No cover art path set for artist: " + id);
    }

    Path coverArtPath = Paths.get(artist.getCoverArtPath());
    if (!Files.isRegularFile(coverArtPath)) {
      throw new EntityDoesNotExistException(
          "Cover art file does not exist for artist: " + id + " at path: " + coverArtPath);
    }

    String probed = Files.probeContentType(coverArtPath);
    MediaType contentType =
        probed != null ? MediaType.parseMediaType(probed) : MediaType.APPLICATION_OCTET_STREAM;

    return ResponseEntity.ok()
        .contentType(contentType)
        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
        .body(new FileSystemResource(coverArtPath));
  }


  @GetMapping("/albums/{id}/coverArt")
  public ResponseEntity<Resource> getAlbumCoverArt(@PathVariable Integer id)
      throws EntityDoesNotExistException, IOException {

    AlbumDto album = songLibraryService.getAlbumById(id);
    if (album.getCoverArtPath() == null) {
      throw new EntityDoesNotExistException("No cover art path set for album: " + id);
    }

    Path coverArtPath = Paths.get(album.getCoverArtPath());
    if (!Files.isRegularFile(coverArtPath)) {
      throw new EntityDoesNotExistException(
          "Cover art file does not exist for album: " + id + " at path: " + coverArtPath);
    }

    String probed = Files.probeContentType(coverArtPath);
    MediaType contentType =
        probed != null ? MediaType.parseMediaType(probed) : MediaType.APPLICATION_OCTET_STREAM;

    return ResponseEntity.ok()
        .contentType(contentType)
        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
        .body(new FileSystemResource(coverArtPath));
  }


  @GetMapping("/songs/{albumId}/{songId}")
  public SongDto getSongById(@PathVariable Integer albumId, @PathVariable Integer songId) {
    return songLibraryService.getSongById(albumId, songId);
  }


  @GetMapping("/songs/random")
  public SongDto getRandomSongFromBackgroundMusicPlaylist() {
    return songLibraryService.getRandomSongFromBackgroundMusicPlaylist();
  }


  // ADMIN ROLE METHODS

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
