package com.djt.jukeanator_engine.domain.songlibrary.service;

import java.util.List;
import com.djt.jukeanator_engine.domain.common.aop.PublicServiceMethod;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumMetadataDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ArtistDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AuthenticateForAdminPanelRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.DownloadAlbumCoverArtRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.GenreTotalsDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchTotalsDto;
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
  // Every method below takes locationId as its first parameter -- standalone/slave instances have
  // exactly one location (their own), while the master instance has one per synced/registered
  // location. See SongLibraryServiceImpl for how the right RootFolderEntity is resolved.
  /**
   *
   * @return
   */
  SearchResultDto getMusicByPopularity(Integer locationId);

  /**
   * The number of items returned per category (artists/albums/songs) by one server page of
   * {@link #getMusicByPopularity}/{@link #getMusicByTitle}/{@link #getMusicBySearch}/{@link
   * #getGenreMusicByPopularity}/{@link #getGenreMusicByTitle}. UI callers use this to size their
   * client-side paging buffers.
   *
   * NOTE: System method, not to be invoked on behalf of a user.
   */
  @PublicServiceMethod
  Integer getSearchResultPageSize();

  /**
   * @param artistPageIndex 0-based server page index for the artists category
   * @param albumPageIndex 0-based server page index for the albums category
   * @param songPageIndex 0-based server page index for the songs category
   * @return
   */
  SearchResultDto getMusicByPopularity(Integer locationId, int artistPageIndex, int albumPageIndex,
      int songPageIndex);

  /**
   *
   * @return
   */
  SearchResultDto getMusicByTitle(Integer locationId);

  /**
   * @param artistPageIndex 0-based server page index for the artists category
   * @param albumPageIndex 0-based server page index for the albums category
   * @param songPageIndex 0-based server page index for the songs category
   * @return
   */
  SearchResultDto getMusicByTitle(Integer locationId, int artistPageIndex, int albumPageIndex,
      int songPageIndex);

  /**
   * @param searchFor
   * @return
   */
  SearchResultDto getMusicBySearch(Integer locationId, String searchFor);

  /**
   * @param searchFor
   * @param artistPageIndex 0-based server page index for the artists category
   * @param albumPageIndex 0-based server page index for the albums category
   * @param songPageIndex 0-based server page index for the songs category
   * @return
   */
  SearchResultDto getMusicBySearch(Integer locationId, String searchFor, int artistPageIndex,
      int albumPageIndex, int songPageIndex);

  /**
   * True, unpaginated totals for {@code searchFor} -- how many artists/albums/songs actually
   * match, regardless of how much has been fetched/paged through so far via
   * {@link #getMusicBySearch}. Used by the Search screen's column headers to show the user how
   * much there is to scroll through.
   *
   * @param searchFor
   * @return
   */
  SearchTotalsDto getSearchTotals(Integer locationId, String searchFor);

  /**
   *
   * @return
   */
  List<GenreDto> getGenres(Integer locationId);

  /**
   * @param genreName
   * @return
   */
  SearchResultDto getGenreMusicByPopularity(Integer locationId, String genreName);

  /**
   * @param genreName
   * @param artistPageIndex 0-based server page index for the artists category
   * @param albumPageIndex 0-based server page index for the albums category
   * @param songPageIndex 0-based server page index for the songs category
   * @return
   */
  SearchResultDto getGenreMusicByPopularity(Integer locationId, String genreName,
      int artistPageIndex, int albumPageIndex, int songPageIndex);

  /**
   * @param genreName
   * @return
   */
  SearchResultDto getGenreMusicByTitle(Integer locationId, String genreName);

  /**
   * @param genreName
   * @param artistPageIndex 0-based server page index for the artists category
   * @param albumPageIndex 0-based server page index for the albums category
   * @param songPageIndex 0-based server page index for the songs category
   * @return
   */
  SearchResultDto getGenreMusicByTitle(Integer locationId, String genreName, int artistPageIndex,
      int albumPageIndex, int songPageIndex);

  /**
   * True, unpaginated totals for {@code genreName} -- how many artists/albums/songs it actually
   * contains, independent of sort order or how much has been paged through so far. Used by the
   * Genres screen header so the user knows how much there is to scroll through.
   *
   * @param genreName
   * @return
   */
  GenreTotalsDto getGenreTotals(Integer locationId, String genreName);

  /**
   *
   * @return
   */
  List<ArtistDto> getArtists(Integer locationId);

  /**
   *
   * @return
   */
  List<AlbumDto> getAlbums(Integer locationId);

  /**
   * @param genreId
   * @return
   */
  List<AlbumDto> getAlbumsForGenre(Integer locationId, Integer genreId);

  /**
   *
   * @param artistName
   * @return
   */
  ArtistDto getArtistByName(Integer locationId, String artistName);

  /**
   *
   * @param artistId
   * @return
   */
  ArtistDto getArtistById(Integer locationId, Integer artistId);

  /**
   *
   * @param albumId
   * @return
   */
  ArtistDto getArtistByAlbumId(Integer locationId, Integer albumId);

  /**
   *
   * @param albumId
   * @return
   */
  AlbumDto getAlbumById(Integer locationId, Integer albumId);

  /**
   *
   * @param albumId
   * @param songId
   * @return
   */
  SongDto getSongById(Integer locationId, Integer albumId, Integer songId);


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
   * @return
   */
  Integer storeSongLibraryAndStatistics();
  
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
   * Returns the shared {@link RootFolderEntity} instance for {@code locationId}, loading it from
   * the repository on first use if not already resident in memory. Intended for use by other
   * services (e.g. {@code SongQueueServiceImpl}) that need read access to a library aggregate root
   * without loading a second copy themselves. On a standalone/slave instance this is always its own
   * one location; on master any previously-synced location's root can be resolved this way.
   *
   * NOTE: System method, not to be invoked on behalf of a user.
   *
   * @return the live {@link RootFolderEntity} for {@code locationId}
   */
  @PublicServiceMethod
  RootFolderEntity getSongLibraryRoot(Integer locationId);

  /**
   * The locationId of the one location this instance itself owns, or {@code null} on the master
   * instance, which owns no location of its own. Standalone/slave instances always have exactly
   * one -- see {@code SongLibraryServiceImpl#initialize()} for how it's resolved.
   *
   * NOTE: System method, not to be invoked on behalf of a user.
   */
  @PublicServiceMethod
  Integer getOwnLocationId();

  /**
   * The full {@link LocationEntity} this instance itself owns (name, logo, coordinates), or
   * {@code null} on the master instance, which owns no location of its own. Mirrors {@link
   * #getOwnLocationId()} but returns the whole entity, for display purposes (e.g. the web UI's
   * own-location name/logo header).
   *
   * NOTE: System method, not to be invoked on behalf of a user.
   */
  @PublicServiceMethod
  LocationEntity getOwnLocation();

  /**
   * Re-runs the same startup discovery {@code SongLibraryServiceImpl}'s constructor uses, so that
   * {@link #getOwnLocationId()}/{@link #getSongLibraryRoot(Integer)} reflect the current state of
   * whatever is persisted on disk/in the repository. Used by {@code SlaveConnectionManager} after
   * correcting a mismatched {@code locationId} post-handshake (see {@code
   * LocationService#reconcileOwnLocationId}), so the in-memory cache doesn't go stale under the
   * id it was keyed by before the correction.
   *
   * NOTE: System method, not to be invoked on behalf of a user.
   */
  @PublicServiceMethod
  void reinitializeOwnLocation();

  /**
   * Renames this instance's own persisted song-library file to match a just-changed location
   * name, so the file takes effect immediately rather than waiting for the next restart's
   * load-time reconciliation. Called right after {@code
   * LocationService#updateOwnLocationInfo} edits this instance's own location's name (e.g. via
   * the Admin Panel's "Edit Location Info" dialog) -- a no-op if {@code previousLocationName}
   * matches the (already-updated) current name, or on master, which owns no location/library of
   * its own.
   *
   * @param previousLocationName this instance's own location name just before the edit
   */
  void renameOwnLocationLibraryFileIfNameChanged(String previousLocationName);

  /**
   * Indicates whether the song library could not be loaded from persisted storage the last time it
   * was (re)initialized — e.g. a fresh install with no library file yet, or a persisted file that
   * could not be read — meaning the in-memory library is currently a freshly-created empty one. Used
   * by the UI to prompt the user to pick a directory to scan for music.
   *
   * @return true if the last load attempt fell back to an empty library
   */
  Boolean isLibraryLoadFailedAtStartup();
}
