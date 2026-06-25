package com.djt.jukeanator_engine.domain.songlibrary.controller;

import static java.util.Objects.requireNonNull;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
 * @author tmyers
 */
@RestController
@RequestMapping("/api/song-library")
public class SongLibraryController implements SongLibraryService {

  private final SongLibraryService songLibraryService;

  public SongLibraryController(
      @Qualifier("songLibraryService") SongLibraryService songLibraryService) {

    requireNonNull(songLibraryService, "songLibraryService cannot be null");
    this.songLibraryService = songLibraryService;
  }

  // USER ROLE METHODS
  @Override
  @GetMapping("/popular")
  public SearchResultDto getMusicByPopularity() {
    return songLibraryService.getMusicByPopularity();
  }

  @Override
  @GetMapping("/search")
  public SearchResultDto getMusicBySearch(@RequestParam String searchFor) {
    return songLibraryService.getMusicBySearch(searchFor);
  }

  @Override
  @GetMapping("/genres")
  public List<GenreDto> getGenres() {
    return songLibraryService.getGenres();
  }

  @Override
  @GetMapping("/genres/popular")
  public SearchResultDto getGenreMusicByPopularity(@RequestParam String genreName) {
    return songLibraryService.getGenreMusicByPopularity(genreName);
  }

  @Override
  @GetMapping("/genres/title")
  public SearchResultDto getGenreMusicByTitle(@RequestParam String genreName) {
    return songLibraryService.getGenreMusicByTitle(genreName);
  }

  @Override
  @GetMapping("/genres/releaseDate")
  public SearchResultDto getGenreMusicByReleaseDate(@RequestParam String genreName) {
    return songLibraryService.getGenreMusicByReleaseDate(genreName);
  }

  @Override
  @GetMapping("/artists")
  public List<ArtistDto> getArtists() {
    return songLibraryService.getArtists();
  }

  @Override
  @GetMapping("/artist")
  public ArtistDto getArtistByName(@RequestParam String artistName) {
    return songLibraryService.getArtistByName(artistName);
  }

  @Override
  @GetMapping("/albums")
  public List<AlbumDto> getAlbums() {
    return songLibraryService.getAlbums();
  }

  @Override
  @GetMapping("/genres/{genreId}/albums")
  public List<AlbumDto> getAlbumsForGenre(@PathVariable Integer genreId) {
    return songLibraryService.getAlbumsForGenre(genreId);
  }

  @Override
  @GetMapping("/albums/{id}")
  public AlbumDto getAlbumById(@PathVariable Integer id) {
    return songLibraryService.getAlbumById(id);
  }

  @Override
  @GetMapping("/songs/{albumId}/{songId}")
  public SongDto getSongById(@PathVariable Integer albumId, @PathVariable Integer songId) {
    return songLibraryService.getSongById(albumId, songId);
  }

  @Override
  @GetMapping("/songs/random")
  public SongDto getRandomSongFromBackgroundMusicPlaylist() {
    return songLibraryService.getRandomSongFromBackgroundMusicPlaylist();
  }


  // ADMIN ROLE METHODS
  @Override
  @PostMapping("/scanNoPath")
  public Integer scanFileSystemForSongs() throws SongScanFailedException {

    return songLibraryService.scanFileSystemForSongs();
  }

  @Override
  @PostMapping("/scan")
  public Integer scanFileSystemForSongs(@RequestBody ScanRequest scanRequest)
      throws SongScanFailedException {

    return songLibraryService.scanFileSystemForSongs(scanRequest);
  }

  @Override
  @PostMapping("/resetSongStatistics")
  public Integer resetSongStatistics() {

    return songLibraryService.resetSongStatistics();
  }

  @Override
  @PostMapping("/restoreSongStatistics")
  public Integer restoreSongStatistics(@RequestBody String filename) {

    return songLibraryService.restoreSongStatistics(filename);
  }

  @Override
  @GetMapping("/searchInternetForAlbumMetadata")
  public List<AlbumMetadataDto> searchInternetForAlbumMetadata(@RequestParam String artistName,
      @RequestParam String albumName, int limit) {

    return songLibraryService.searchInternetForAlbumMetadata(artistName, albumName, limit);
  }

  @Override
  @PostMapping("/albums/{albumId}/updateAlbumMetadata")
  public AlbumMetadataDto updateAlbumMetadata(@PathVariable Integer albumId,
      @RequestBody AlbumMetadataDto albumMetadata) {

    return songLibraryService.updateAlbumMetadata(albumId, albumMetadata);
  }

  @Override
  @PostMapping("/downloadAlbumCoverArt")
  public String downloadAlbumCoverArt(
      @RequestBody DownloadAlbumCoverArtRequest downloadAlbumCoverArtRequest) {

    return songLibraryService.downloadAlbumCoverArt(downloadAlbumCoverArtRequest);
  }

  @Override
  @PostMapping("/authenticateForAdminPanel")
  public Boolean authenticateForAdminPanel(
      @RequestBody AuthenticateForAdminPanelRequest authenticateForAdminPanelRequest) {
    return songLibraryService.authenticateForAdminPanel(authenticateForAdminPanelRequest);
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
  public RootFolderEntity getRootFolderEntity() {
    throw new UnsupportedOperationException("This method cannot be invoked by a user");
  }
}
