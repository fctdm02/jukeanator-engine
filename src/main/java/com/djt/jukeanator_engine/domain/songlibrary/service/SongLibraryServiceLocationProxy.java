package com.djt.jukeanator_engine.domain.songlibrary.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotAlbumDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotArtistDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotGenreDto;
import com.djt.jukeanator_engine.domain.location.dto.LibrarySnapshotSongDto;
import com.djt.jukeanator_engine.domain.location.exception.LocationServiceException;
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
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songqueue.event.MultipleSongsAddedToQueueEvent;
import com.djt.jukeanator_engine.domain.songqueue.event.SongAddedToQueueEvent;

/**
 * Master-only, one instance per locationId. Unlike {@code SongQueueServiceLocationProxy}, reads
 * here never touch the live {@code /ws-slave} connection — they're served straight from that
 * location's most recent synced {@link LibrarySnapshotDto} on master's own disk (see
 * {@code LocationService#getLibrarySnapshot}), since library metadata is comparatively static and
 * this avoids putting browse/search load on the fragile persistent-connection path. Cover art is
 * served by a dedicated location-scoped controller endpoint, not through this service.
 *
 * <p>
 * Search here uses simple case-insensitive substring matching rather than the weighted scoring
 * algorithm {@code SongLibraryServiceImpl} uses locally — a deliberate scope reduction for this
 * phase, still functionally correct, just less fancy ranking.
 *
 * @author tmyers
 */
public class SongLibraryServiceLocationProxy implements SongLibraryService {

  private static final String SYSTEM_METHOD_MESSAGE =
      "System method, not to be invoked on behalf of a user!";
  private static final String ADMIN_METHOD_MESSAGE =
      "Admin/scan methods are not supported on a master-side location proxy — they are inherently "
          + "local to the slave that owns the library.";

  private final String locationId;
  private final LocationService locationService;

  public SongLibraryServiceLocationProxy(String locationId, LocationService locationService) {
    this.locationId = locationId;
    this.locationService = locationService;
  }

  private LibrarySnapshotDto snapshot() {
    LibrarySnapshotDto snapshot = locationService.getLibrarySnapshot(locationId);
    return snapshot != null ? snapshot : new LibrarySnapshotDto(List.of(), List.of(), List.of());
  }

  private String artistName(LibrarySnapshotDto snap, Integer artistId) {
    return snap.artists().stream().filter(a -> a.sourceArtistId().equals(artistId))
        .map(LibrarySnapshotArtistDto::name).findFirst().orElse(null);
  }

  private int numPlays(LibrarySnapshotAlbumDto album) {
    return album.songs().stream().mapToInt(s -> s.numPlays() == null ? 0 : s.numPlays()).sum();
  }

  private AlbumDto toAlbumDto(LibrarySnapshotDto snap, LibrarySnapshotAlbumDto album) {

    List<SongDto> songs = new ArrayList<>();
    for (LibrarySnapshotSongDto song : album.songs()) {
      songs.add(toSongDto(snap, album, song));
    }
    return new AlbumDto(album.sourceGenreId(), album.genreName(), album.sourceArtistId(),
        album.artistName(), album.sourceAlbumId(), album.name(), album.hasExplicit(),
        album.recordLabel(), album.releaseDate(), null, album.isCompilation(), songs);
  }

  private SongDto toSongDto(LibrarySnapshotDto snap, LibrarySnapshotAlbumDto album,
      LibrarySnapshotSongDto song) {

    return new SongDto(album.sourceGenreId(), album.genreName(), album.sourceArtistId(),
        album.artistName(), album.sourceAlbumId(), album.name(), null, song.sourceSongId(),
        song.title(), song.trackNumber(), song.numPlays());
  }

  /**
   * Groups albums to an artist by {@code artistName}, not {@code sourceArtistId} — an album's own
   * artist id does not reliably correspond to the same id space as the top-level artist list (e.g.
   * a compilation album credits its songs' real artist while the album's own parent-folder artist
   * id may be a "Compilations" placeholder), whereas the denormalized name is always correct.
   */
  private ArtistDto artistDtoByName(LibrarySnapshotDto snap, String name) {

    if (name == null) {
      return null;
    }
    Integer artistId = snap.artists().stream().filter(a -> name.equals(a.name()))
        .map(LibrarySnapshotArtistDto::sourceArtistId).findFirst().orElse(null);
    List<AlbumDto> albums = snap.albums().stream().filter(a -> name.equals(a.artistName()))
        .map(a -> toAlbumDto(snap, a)).toList();
    int songCount = albums.stream().mapToInt(AlbumDto::getNumSongs).sum();
    int numPlays = albums.stream().mapToInt(AlbumDto::getNumPlays).sum();
    return new ArtistDto(artistId, name, null, albums.size(), songCount, numPlays, albums);
  }

  private ArtistDto toArtistDto(LibrarySnapshotDto snap, Integer artistId) {
    return artistDtoByName(snap, artistName(snap, artistId));
  }

  @Override
  public SearchResultDto getMusicByPopularity() {

    LibrarySnapshotDto snap = snapshot();
    List<AlbumDto> albums = snap.albums().stream().map(a -> toAlbumDto(snap, a))
        .sorted(Comparator.comparingInt(AlbumDto::getNumPlays).reversed()).toList();
    List<SongDto> songs = albums.stream().flatMap(a -> a.getSongs().stream())
        .sorted(Comparator.comparingInt(SongDto::getNumPlays).reversed()).toList();
    return new SearchResultDto(songs, List.of(), albums);
  }

  @Override
  public SearchResultDto getMusicBySearch(String searchFor) {
    return getMusicBySearch(searchFor, Integer.MAX_VALUE);
  }

  @Override
  public SearchResultDto getMusicBySearch(String searchFor, int limit) {

    LibrarySnapshotDto snap = snapshot();
    String needle = searchFor == null ? "" : searchFor.toLowerCase();

    List<ArtistDto> artists = snap.artists().stream()
        .filter(a -> a.name().toLowerCase().contains(needle))
        .map(a -> toArtistDto(snap, a.sourceArtistId())).limit(limit).toList();

    List<AlbumDto> albums = snap.albums().stream().filter(a -> a.name().toLowerCase().contains(needle))
        .map(a -> toAlbumDto(snap, a)).limit(limit).toList();

    List<SongDto> songs = snap.albums().stream().flatMap(a -> a.songs().stream()
        .filter(s -> s.title().toLowerCase().contains(needle)).map(s -> toSongDto(snap, a, s)))
        .limit(limit).toList();

    return new SearchResultDto(songs, artists, albums);
  }

  @Override
  public List<GenreDto> getGenres() {

    LibrarySnapshotDto snap = snapshot();
    List<GenreDto> genres = new ArrayList<>();
    for (LibrarySnapshotGenreDto genre : snap.genres()) {
      List<LibrarySnapshotAlbumDto> genreAlbums = snap.albums().stream()
          .filter(a -> genre.sourceGenreId().equals(a.sourceGenreId())).toList();
      List<Integer> albumIds = genreAlbums.stream().map(LibrarySnapshotAlbumDto::sourceAlbumId).toList();
      int numPlays = genreAlbums.stream().mapToInt(this::numPlays).sum();
      genres.add(new GenreDto(genre.sourceGenreId(), genre.name(), albumIds, numPlays));
    }
    return genres;
  }

  @Override
  public SearchResultDto getGenreMusicByPopularity(String genreName) {
    return genreMusic(genreName, Comparator.comparingInt(AlbumDto::getNumPlays).reversed());
  }

  @Override
  public SearchResultDto getGenreMusicByTitle(String genreName) {
    return genreMusic(genreName, Comparator.comparing(AlbumDto::getAlbumName));
  }

  private SearchResultDto genreMusic(String genreName, Comparator<AlbumDto> order) {

    LibrarySnapshotDto snap = snapshot();
    List<AlbumDto> albums = snap.albums().stream()
        .filter(a -> genreName.equalsIgnoreCase(a.genreName()))
        .map(a -> toAlbumDto(snap, a)).sorted(order).toList();
    List<SongDto> songs = albums.stream().flatMap(a -> a.getSongs().stream()).toList();
    return new SearchResultDto(songs, List.of(), albums);
  }

  @Override
  public List<ArtistDto> getArtists() {

    LibrarySnapshotDto snap = snapshot();
    return snap.artists().stream().map(a -> toArtistDto(snap, a.sourceArtistId())).toList();
  }

  @Override
  public List<AlbumDto> getAlbums() {

    LibrarySnapshotDto snap = snapshot();
    return snap.albums().stream().map(a -> toAlbumDto(snap, a)).toList();
  }

  @Override
  public List<AlbumDto> getAlbumsForGenre(Integer genreId) {

    LibrarySnapshotDto snap = snapshot();
    return snap.albums().stream().filter(a -> genreId.equals(a.sourceGenreId()))
        .map(a -> toAlbumDto(snap, a)).toList();
  }

  @Override
  public ArtistDto getArtistByName(String artistName) {

    LibrarySnapshotDto snap = snapshot();
    return snap.artists().stream().filter(a -> a.name().equalsIgnoreCase(artistName))
        .findFirst().map(a -> toArtistDto(snap, a.sourceArtistId())).orElse(null);
  }

  @Override
  public ArtistDto getArtistById(Integer artistId) {
    return toArtistDto(snapshot(), artistId);
  }

  @Override
  public ArtistDto getArtistByAlbumId(Integer albumId) {

    LibrarySnapshotDto snap = snapshot();
    return snap.albums().stream().filter(a -> albumId.equals(a.sourceAlbumId())).findFirst()
        .map(a -> artistDtoByName(snap, a.artistName())).orElse(null);
  }

  @Override
  public AlbumDto getAlbumById(Integer albumId) {

    LibrarySnapshotDto snap = snapshot();
    return snap.albums().stream().filter(a -> albumId.equals(a.sourceAlbumId())).findFirst()
        .map(a -> toAlbumDto(snap, a)).orElseThrow(() -> new LocationServiceException(
            "No album with id " + albumId + " synced for locationId: " + locationId));
  }

  @Override
  public SongDto getSongById(Integer albumId, Integer songId) {

    LibrarySnapshotDto snap = snapshot();
    LibrarySnapshotAlbumDto album = snap.albums().stream()
        .filter(a -> albumId.equals(a.sourceAlbumId())).findFirst()
        .orElseThrow(() -> new LocationServiceException(
            "No album with id " + albumId + " synced for locationId: " + locationId));
    LibrarySnapshotSongDto song = album.songs().stream()
        .filter(s -> songId.equals(s.sourceSongId())).findFirst()
        .orElseThrow(() -> new LocationServiceException(
            "No song with id " + songId + " in album " + albumId + " for locationId: " + locationId));
    return toSongDto(snap, album, song);
  }

  @Override
  public Integer scanFileSystemForSongs(ScanRequest request) throws SongScanFailedException {
    throw new UnsupportedOperationException(ADMIN_METHOD_MESSAGE);
  }

  @Override
  public Integer scanFileSystemForSongs() throws SongScanFailedException {
    throw new UnsupportedOperationException(ADMIN_METHOD_MESSAGE);
  }

  @Override
  public Integer resetSongStatistics() {
    throw new UnsupportedOperationException(ADMIN_METHOD_MESSAGE);
  }

  @Override
  public Integer restoreSongStatistics(String filename) {
    throw new UnsupportedOperationException(ADMIN_METHOD_MESSAGE);
  }

  @Override
  public List<AlbumMetadataDto> searchInternetForAlbumMetadata(String artistName, String albumName,
      int limit) {
    throw new UnsupportedOperationException(ADMIN_METHOD_MESSAGE);
  }

  @Override
  public AlbumMetadataDto updateAlbumMetadata(Integer albumId, AlbumMetadataDto albumMetadata) {
    throw new UnsupportedOperationException(ADMIN_METHOD_MESSAGE);
  }

  @Override
  public String downloadAlbumCoverArt(DownloadAlbumCoverArtRequest downloadAlbumCoverArtRequest) {
    throw new UnsupportedOperationException(ADMIN_METHOD_MESSAGE);
  }

  @Override
  public Boolean authenticateForAdminPanel(
      AuthenticateForAdminPanelRequest authenticateForAdminPanelRequest) {
    throw new UnsupportedOperationException(SYSTEM_METHOD_MESSAGE);
  }

  @Override
  public void handleSongAddedToQueueEvent(SongAddedToQueueEvent event) {
    throw new UnsupportedOperationException(SYSTEM_METHOD_MESSAGE);
  }

  @Override
  public void handleMultipleSongsAddedToQueueEvent(MultipleSongsAddedToQueueEvent event) {
    throw new UnsupportedOperationException(SYSTEM_METHOD_MESSAGE);
  }

  @Override
  public RootFolderEntity getSongLibraryRoot() {
    throw new UnsupportedOperationException(SYSTEM_METHOD_MESSAGE);
  }
}
