package com.djt.jukeanator_engine.domain.songlibrary.service;

import java.util.List;
import com.djt.jukeanator_engine.domain.common.aop.PublicServiceMethod;
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
import com.djt.jukeanator_engine.domain.songqueue.event.MultipleSongsAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;

/**
 * @author tmyers
 */
public interface SongLibraryService {

  // USER ROLE METHODS
  /**
   * 
   * @return
   */
  SearchResultDto getMusicByPopularity();

  /**
   * @param searchFor
   * @return
   */
  SearchResultDto getMusicBySearch(String searchFor);

  /**
   * @param searchFor
   * @param limit maximum number of results per category; defaults to the service-level setting
   * @return
   */
  SearchResultDto getMusicBySearch(String searchFor, int limit);

  /**
   * 
   * @return
   */
  List<GenreDto> getGenres();

  /**
   * @param genreName
   * @return
   */
  SearchResultDto getGenreMusicByPopularity(String genreName);

  /**
   * @param genreName
   * @return
   */
  SearchResultDto getGenreMusicByTitle(String genreName);

  /**
   * 
   * @return
   */
  List<ArtistDto> getArtists();

  /**
   * 
   * @return
   */
  List<AlbumDto> getAlbums();

  /**
   * @param genreId
   * @return
   */
  List<AlbumDto> getAlbumsForGenre(Integer genreId);

  /**
   * 
   * @param artistName
   * @return
   */
  ArtistDto getArtistByName(String artistName);

  /**
   * 
   * @param artistId
   * @return
   */
  ArtistDto getArtistById(Integer artistId);

  /**
   * 
   * @param albumId
   * @return
   */
  ArtistDto getArtistByAlbumId(Integer albumId);

  /**
   * 
   * @param albumId
   * @return
   */
  AlbumDto getAlbumById(Integer albumId);

  /**
   * 
   * @param albumId
   * @param songId
   * @return
   */
  SongDto getSongById(Integer albumId, Integer songId);


  // ADMIN ROLE METHODS
  /**
   * 
   * @param scanRequest
   * @return number of albums scanned
   * @throws SongScanFailedException
   */
  Integer scanFileSystemForSongs(ScanRequest request) throws SongScanFailedException;

  /**
   * 
   * @return
   * @throws SongScanFailedException
   */
  Integer scanFileSystemForSongs() throws SongScanFailedException;

  /**
   * @return
   */
  Integer resetSongStatistics();

  /**
   * 
   * @param filename
   * @return
   */
  Integer restoreSongStatistics(String filename);

  /**
   * 
   * @param artistName
   * @param albumName
   * @param limit
   * @return
   */
  List<AlbumMetadataDto> searchInternetForAlbumMetadata(String artistName, String albumName,
      int limit);

  /**
   * 
   * @param albumId
   * @param albumMetadata
   * @return
   */
  AlbumMetadataDto updateAlbumMetadata(Integer albumId, AlbumMetadataDto albumMetadata);

  /**
   * 
   * @param downloadAlbumCoverArtRequest
   * @return
   */
  String downloadAlbumCoverArt(DownloadAlbumCoverArtRequest downloadAlbumCoverArtRequest);

  // SYSTEM METHODS (not to be invoked on behalf of a user)
  /**
   * 
   * @param authenticateForAdminPanelRequest Contains username and password fields
   * @return True, if authentication was successful for either admin or owner accounts.
   */
  @PublicServiceMethod
  Boolean authenticateForAdminPanel(
      AuthenticateForAdminPanelRequest authenticateForAdminPanelRequest);

  /**
   * 
   * @param event
   */
  @PublicServiceMethod
  void handleSongAddedToQueueEvent(SongAddedToQueueEvent event);

  /**
   * 
   * @param event
   */
  @PublicServiceMethod
  void handleMultipleSongsAddedToQueueEvent(MultipleSongsAddedToQueueEvent event);

  /**
   * Returns the single shared {@link RootFolderEntity} instance managed by this service. Intended
   * for use by other services (e.g. {@code SongQueueServiceImpl}) that need read access to the
   * library aggregate root without loading a second copy from the repository.
   *
   * NOTE: System method, not to be invoked on behalf of a user.
   *
   * @return the live {@link RootFolderEntity} held by this service
   */
  @PublicServiceMethod
  RootFolderEntity getSongLibraryRoot();
}
