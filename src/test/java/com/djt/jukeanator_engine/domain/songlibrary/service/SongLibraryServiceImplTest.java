package com.djt.jukeanator_engine.domain.songlibrary.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import com.djt.jukeanator_engine.config.AppProperties;
import com.djt.jukeanator_engine.domain.common.exception.EntityAlreadyExistsException;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.location.model.LocationEntity;
import com.djt.jukeanator_engine.domain.location.service.LocationService;
import com.djt.jukeanator_engine.domain.songlibrary.dto.AlbumDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.ScanRequest;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SearchResultDto;
import com.djt.jukeanator_engine.domain.songlibrary.dto.SongDto;
import com.djt.jukeanator_engine.domain.songlibrary.event.ScanFileSystemForSongsEvent;
import com.djt.jukeanator_engine.domain.songlibrary.exception.SongLibraryServiceException;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.FolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryObjectPersistor;
import com.djt.jukeanator_engine.domain.songlibrary.repository.SongLibraryRepository;
import com.djt.jukeanator_engine.domain.songlibrary.service.utils.SongScanner;

/**
 * Covers {@link SongLibraryServiceImpl#renameOwnLocationLibraryFileIfNameChanged}, locally
 * constructed with mocked dependencies (fast, deterministic, no Spring context needed) -- unlike
 * {@code SongLibraryServiceTest}, which exercises a Spring-managed bean end-to-end. The actual
 * file rename mechanics are covered separately by {@code
 * SongLibraryRepositoryFileSystemImplTest#renameLocationLibraryFile_*}; this only covers the
 * service-level wiring (deriving old/new names, the no-op-when-unchanged check, and the
 * master-mode guard).
 */
public class SongLibraryServiceImplTest {

  private SongLibraryServiceImpl newService(boolean isMaster, String ownLocationName,
      SongLibraryRepository songLibraryRepository, LocationService locationService)
      throws EntityDoesNotExistException {

    AppProperties appProperties = new AppProperties();
    appProperties.setDataDir("unused-for-this-test");
    appProperties.setMode(isMaster ? "master" : "standalone");

    SongScanner songScanner = mock(SongScanner.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    if (!isMaster) {
      LocationEntity ownLocation =
          new LocationEntity(1, ownLocationName, null, null, "test-api-key-hash");
      // appProperties.getLocationId() is null unless configured (not set above), so
      // initialize() calls getOrCreateOwnLocation(null) -- standalone mode's own-first-boot path.
      when(locationService.getOrCreateOwnLocation(null)).thenReturn(ownLocation);
      // Simplest "fresh install, no .oos file yet" setup -- initialize() falls back to an empty
      // placeholder root, which is still wired up to ownLocation the same as a real load.
      when(songLibraryRepository.loadAggregateRoot(anyInt()))
          .thenThrow(new EntityDoesNotExistException("no library yet"));
    }

    return new SongLibraryServiceImpl(appProperties, songLibraryRepository, locationService,
        songScanner, Integer.valueOf(100), eventPublisher);
  }

  @Test
  void renameOwnLocationLibraryFileIfNameChanged_delegatesToRepository_whenNameChanged()
      throws Exception {

    SongLibraryRepository songLibraryRepository = mock(SongLibraryRepository.class);
    LocationService locationService = mock(LocationService.class);
    SongLibraryServiceImpl service =
        newService(false, "New Name", songLibraryRepository, locationService);

    service.renameOwnLocationLibraryFileIfNameChanged("Old Name");

    verify(songLibraryRepository).renameLocationLibraryFile("Old Name", "New Name");
  }

  @Test
  void renameOwnLocationLibraryFileIfNameChanged_isNoOp_whenNameUnchanged() throws Exception {

    SongLibraryRepository songLibraryRepository = mock(SongLibraryRepository.class);
    LocationService locationService = mock(LocationService.class);
    SongLibraryServiceImpl service =
        newService(false, "Same Name", songLibraryRepository, locationService);

    service.renameOwnLocationLibraryFileIfNameChanged("Same Name");

    verify(songLibraryRepository, never()).renameLocationLibraryFile(anyString(), anyString());
  }

  @Test
  void renameOwnLocationLibraryFileIfNameChanged_throws_onMaster() throws Exception {

    SongLibraryRepository songLibraryRepository = mock(SongLibraryRepository.class);
    LocationService locationService = mock(LocationService.class);
    SongLibraryServiceImpl service =
        newService(true, null, songLibraryRepository, locationService);

    assertThrows(SongLibraryServiceException.class,
        () -> service.renameOwnLocationLibraryFileIfNameChanged("Old Name"));
  }

  @Test
  void renameOwnLocationLibraryFileIfNameChanged_alsoRenamesJpaFilesystemBackup(
      @TempDir Path tempDir) throws Exception {

    SongLibraryRepository songLibraryRepository = mock(SongLibraryRepository.class);
    LocationService locationService = mock(LocationService.class);
    SongScanner songScanner = mock(SongScanner.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    LocationEntity ownLocation = new LocationEntity(1, "New Name", null, null, "hash");

    when(locationService.getOrCreateOwnLocation(null)).thenReturn(ownLocation);
    when(songLibraryRepository.loadAggregateRoot(1))
        .thenThrow(new EntityDoesNotExistException("no library yet"));

    AppProperties appProperties = new AppProperties();
    appProperties.setDataDir(tempDir.toString());
    appProperties.setMode("standalone");
    appProperties.setRepositoryType("jpa");

    SongLibraryServiceImpl service = new SongLibraryServiceImpl(appProperties,
        songLibraryRepository, locationService, songScanner, Integer.valueOf(100), eventPublisher);

    // Simulates a .oos backup already on disk under the pre-rename name, left behind by an
    // earlier storeSongLibraryAndStatistics() call (see that method's JPA-backup test above).
    Path oldBackupFile = tempDir.resolve("Old_Name.oos");
    Files.createFile(oldBackupFile);

    service.renameOwnLocationLibraryFileIfNameChanged("Old Name");

    verify(songLibraryRepository).renameLocationLibraryFile("Old Name", "New Name");
    assertFalse(Files.exists(oldBackupFile),
        "the stale .oos backup under the old location name should have been renamed away");
    assertTrue(Files.exists(tempDir.resolve("New_Name.oos")),
        "the .oos backup should now exist under the new location name so it doesn't linger as an "
            + "orphan until the next storeSongLibraryAndStatistics() call");
  }

  // ─────────────────────────────────────────────────────────────────────────
  // scanFileSystemForSongs -- JPA round-trip integrity check
  // ─────────────────────────────────────────────────────────────────────────

  private SongLibraryServiceImpl newScanService(String repositoryType, String dataDir,
      SongLibraryRepository songLibraryRepository, LocationService locationService,
      SongScanner songScanner, ApplicationEventPublisher eventPublisher, LocationEntity ownLocation)
      throws EntityDoesNotExistException {

    AppProperties appProperties = new AppProperties();
    appProperties.setDataDir(dataDir);
    appProperties.setMode("standalone");
    appProperties.setRepositoryType(repositoryType);

    when(locationService.getOrCreateOwnLocation(null)).thenReturn(ownLocation);
    // Simplest "fresh install, no .oos file yet" setup -- initialize() falls back to an empty
    // placeholder root, which is still wired up to ownLocation the same as a real load.
    when(songLibraryRepository.loadAggregateRoot(anyInt()))
        .thenThrow(new EntityDoesNotExistException("no library yet"));

    return new SongLibraryServiceImpl(appProperties, songLibraryRepository, locationService,
        songScanner, Integer.valueOf(100), eventPublisher);
  }

  private RootFolderEntity buildSmallRoot(String rootPath, String albumName)
      throws EntityAlreadyExistsException {

    RootFolderEntity root = new RootFolderEntity(rootPath);
    root.setId(1);

    GenreFolderEntity genre = new GenreFolderEntity(root, "Rock");
    genre.setId(2);
    root.addChildFolder(genre);

    ArtistFolderEntity artist = new ArtistFolderEntity(genre, "Artist One");
    artist.setId(3);
    genre.addChildFolder(artist);

    AlbumFolderEntity album = new AlbumFolderEntity(artist, albumName);
    album.setId(4);
    artist.addChildFolder(album);
    album.createCoverArtEntity();
    album.createMetadataEntity();
    album.getMetaData().setLoaded(true);

    SongFileEntity song = new SongFileEntity(album, "Song.mp3");
    song.setId(5);
    song.setArtistName("Artist One");
    song.setSongName("Song");
    song.setTrackNumber(1);
    song.setNumPlays(0);
    album.addChildSong(song);

    root.initialize();
    return root;
  }

  @Test
  void scanFileSystemForSongs_throwsSongLibraryServiceException_whenJpaReloadDiverges()
      throws Exception {

    SongLibraryRepository songLibraryRepository = mock(SongLibraryRepository.class);
    LocationService locationService = mock(LocationService.class);
    SongScanner songScanner = mock(SongScanner.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    LocationEntity ownLocation = new LocationEntity(1, "Test Location", null, null, "hash");

    SongLibraryServiceImpl service = newScanService("jpa", "unused-for-this-test",
        songLibraryRepository, locationService, songScanner, eventPublisher, ownLocation);

    RootFolderEntity scannedRoot = buildSmallRoot("/scan/path", "Scanned Album");
    RootFolderEntity rehydratedRoot = buildSmallRoot("/scan/path", "Different Album");

    when(songScanner.scanFileSystemForSongs(anyString())).thenReturn(scannedRoot);
    // doReturn(...).when(...), not when(...).thenReturn(...) -- the mock is already stubbed (in
    // newScanService) to throw for loadAggregateRoot(anyInt()), and when(...) would actually
    // invoke that existing stub while setting up this one, throwing before .thenReturn() ever
    // gets attached.
    doReturn(rehydratedRoot).when(songLibraryRepository).loadAggregateRoot(1);

    assertThrows(SongLibraryServiceException.class,
        () -> service.scanFileSystemForSongs(new ScanRequest("/scan/path")));

    verify(eventPublisher, never()).publishEvent(any(ScanFileSystemForSongsEvent.class));
  }

  @Test
  void scanFileSystemForSongs_succeeds_whenJpaReloadMatches(@TempDir Path tempDir)
      throws Exception {

    SongLibraryRepository songLibraryRepository = mock(SongLibraryRepository.class);
    LocationService locationService = mock(LocationService.class);
    SongScanner songScanner = mock(SongScanner.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    LocationEntity ownLocation = new LocationEntity(1, "Test Location", null, null, "hash");

    SongLibraryServiceImpl service = newScanService("jpa", tempDir.toString(),
        songLibraryRepository, locationService, songScanner, eventPublisher, ownLocation);

    RootFolderEntity scannedRoot = buildSmallRoot("/scan/path", "Same Album");
    RootFolderEntity rehydratedRoot = buildSmallRoot("/scan/path", "Same Album");

    when(songScanner.scanFileSystemForSongs(anyString())).thenReturn(scannedRoot);
    // doReturn(...).when(...), not when(...).thenReturn(...) -- the mock is already stubbed (in
    // newScanService) to throw for loadAggregateRoot(anyInt()), and when(...) would actually
    // invoke that existing stub while setting up this one, throwing before .thenReturn() ever
    // gets attached.
    doReturn(rehydratedRoot).when(songLibraryRepository).loadAggregateRoot(1);

    assertDoesNotThrow(() -> service.scanFileSystemForSongs(new ScanRequest("/scan/path")));

    verify(eventPublisher).publishEvent(any(ScanFileSystemForSongsEvent.class));
  }

  @Test
  void scanFileSystemForSongs_skipsIntegrityCheck_whenRepositoryTypeIsFilesystem(
      @TempDir Path tempDir) throws Exception {

    SongLibraryRepository songLibraryRepository = mock(SongLibraryRepository.class);
    LocationService locationService = mock(LocationService.class);
    SongScanner songScanner = mock(SongScanner.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    LocationEntity ownLocation = new LocationEntity(1, "Test Location", null, null, "hash");

    SongLibraryServiceImpl service = newScanService("filesystem", tempDir.toString(),
        songLibraryRepository, locationService, songScanner, eventPublisher, ownLocation);

    RootFolderEntity scannedRoot = buildSmallRoot("/scan/path", "Scanned Album");
    RootFolderEntity mismatchedRoot = buildSmallRoot("/scan/path", "Totally Different Album");

    when(songScanner.scanFileSystemForSongs(anyString())).thenReturn(scannedRoot);
    // Even though this would diverge from scannedRoot, the check must never run for the
    // filesystem backend, so loadAggregateRoot should never even be consulted for this purpose.
    doReturn(mismatchedRoot).when(songLibraryRepository).loadAggregateRoot(1);

    assertDoesNotThrow(() -> service.scanFileSystemForSongs(new ScanRequest("/scan/path")));

    verify(eventPublisher).publishEvent(any(ScanFileSystemForSongsEvent.class));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // initialize() -- adopting a leftover local .oos file into a JPA store that has no row yet
  // (e.g. this dataDir was previously run in filesystem mode)
  // ─────────────────────────────────────────────────────────────────────────

  private RootFolderEntity newEmptyRoot(String rootPath, int id, LocationEntity parentLocation) {

    RootFolderEntity root = new RootFolderEntity(rootPath);
    root.setId(id);
    root.setParentLocation(parentLocation);
    root.initialize();
    return root;
  }

  @Test
  void initialize_adoptsLocalOosFile_intoJpaStore_whenDatabaseHasNoRowYet(@TempDir Path tempDir)
      throws Exception {

    SongLibraryRepository songLibraryRepository = mock(SongLibraryRepository.class);
    LocationService locationService = mock(LocationService.class);
    SongScanner songScanner = mock(SongScanner.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    LocationEntity ownLocation = new LocationEntity(1, "Location Name", null, null, "hash");

    RootFolderEntity localRoot = newEmptyRoot("/scan/path", 1, ownLocation);
    new SongLibraryObjectPersistor().writeSongLibraryToDisk(localRoot,
        tempDir.resolve("Location_Name.oos").toString());

    RootFolderEntity rehydratedRoot = newEmptyRoot("/scan/path", 1, ownLocation);

    when(locationService.getOrCreateOwnLocation(null)).thenReturn(ownLocation);
    // First call (inside loadOwnRoot()) simulates "no row for this location yet" -- the JPA store
    // hasn't been populated. Second call (the post-adoption round-trip check) returns the
    // structurally-matching reloaded tree.
    when(songLibraryRepository.loadAggregateRoot(1))
        .thenThrow(new EntityDoesNotExistException("no library yet"))
        .thenReturn(rehydratedRoot);

    AppProperties appProperties = new AppProperties();
    appProperties.setDataDir(tempDir.toString());
    appProperties.setMode("standalone");
    appProperties.setRepositoryType("jpa");

    SongLibraryServiceImpl service = new SongLibraryServiceImpl(appProperties,
        songLibraryRepository, locationService, songScanner, Integer.valueOf(100), eventPublisher);

    verify(songLibraryRepository).storeAggregateRoot(any(RootFolderEntity.class));
    assertSame(rehydratedRoot, service.getSongLibraryRoot(1));
    assertFalse(service.isLibraryLoadFailedAtStartup());
  }

  @Test
  void initialize_throws_whenAdoptedOosFileDivergesFromReloadedJpaTree(@TempDir Path tempDir)
      throws Exception {

    SongLibraryRepository songLibraryRepository = mock(SongLibraryRepository.class);
    LocationService locationService = mock(LocationService.class);
    SongScanner songScanner = mock(SongScanner.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    LocationEntity ownLocation = new LocationEntity(1, "Location Name", null, null, "hash");

    RootFolderEntity localRoot = newEmptyRoot("/scan/path", 1, ownLocation);
    new SongLibraryObjectPersistor().writeSongLibraryToDisk(localRoot,
        tempDir.resolve("Location_Name.oos").toString());

    RootFolderEntity mismatchedRoot = newEmptyRoot("/different/scan/path", 1, ownLocation);

    when(locationService.getOrCreateOwnLocation(null)).thenReturn(ownLocation);
    when(songLibraryRepository.loadAggregateRoot(1))
        .thenThrow(new EntityDoesNotExistException("no library yet"))
        .thenReturn(mismatchedRoot);

    AppProperties appProperties = new AppProperties();
    appProperties.setDataDir(tempDir.toString());
    appProperties.setMode("standalone");
    appProperties.setRepositoryType("jpa");

    assertThrows(SongLibraryServiceException.class,
        () -> new SongLibraryServiceImpl(appProperties, songLibraryRepository, locationService,
            songScanner, Integer.valueOf(100), eventPublisher));
  }

  @Test
  void initialize_fallsBackToEmptyPlaceholder_whenJpaHasNoRowAndNoLocalOosFile(
      @TempDir Path tempDir) throws Exception {

    SongLibraryRepository songLibraryRepository = mock(SongLibraryRepository.class);
    LocationService locationService = mock(LocationService.class);
    SongScanner songScanner = mock(SongScanner.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    LocationEntity ownLocation = new LocationEntity(1, "Location Name", null, null, "hash");

    when(locationService.getOrCreateOwnLocation(null)).thenReturn(ownLocation);
    when(songLibraryRepository.loadAggregateRoot(1))
        .thenThrow(new EntityDoesNotExistException("no library yet"));

    AppProperties appProperties = new AppProperties();
    appProperties.setDataDir(tempDir.toString());
    appProperties.setMode("standalone");
    appProperties.setRepositoryType("jpa");

    SongLibraryServiceImpl service = new SongLibraryServiceImpl(appProperties,
        songLibraryRepository, locationService, songScanner, Integer.valueOf(100), eventPublisher);

    verify(songLibraryRepository, never()).storeAggregateRoot(any(RootFolderEntity.class));
    assertTrue(service.isLibraryLoadFailedAtStartup());
    assertEquals("", service.getSongLibraryRoot(1).getRootPath());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // storeSongLibraryAndStatistics -- JPA also keeps a filesystem (.oos) backup and CDStats.TXT
  // ─────────────────────────────────────────────────────────────────────────

  @Test
  void storeSongLibraryAndStatistics_writesFilesystemBackupAndCdStats_whenRepositoryTypeIsJpa(
      @TempDir Path tempDir) throws Exception {

    SongLibraryRepository songLibraryRepository = mock(SongLibraryRepository.class);
    LocationService locationService = mock(LocationService.class);
    SongScanner songScanner = mock(SongScanner.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    LocationEntity ownLocation = new LocationEntity(1, "Location Name", null, null, "hash");

    // initialize() adopts this leftover local .oos file into the (mocked, empty) JPA store, so
    // ownRoot ends up populated without needing a full scan -- see
    // initialize_adoptsLocalOosFile_intoJpaStore_whenDatabaseHasNoRowYet above.
    RootFolderEntity localRoot = newEmptyRoot("/scan/path", 1, ownLocation);
    Path oosFile = tempDir.resolve("Location_Name.oos");
    new SongLibraryObjectPersistor().writeSongLibraryToDisk(localRoot, oosFile.toString());

    RootFolderEntity rehydratedRoot = newEmptyRoot("/scan/path", 1, ownLocation);

    when(locationService.getOrCreateOwnLocation(null)).thenReturn(ownLocation);
    when(songLibraryRepository.loadAggregateRoot(1))
        .thenThrow(new EntityDoesNotExistException("no library yet"))
        .thenReturn(rehydratedRoot);

    AppProperties appProperties = new AppProperties();
    appProperties.setDataDir(tempDir.toString());
    appProperties.setMode("standalone");
    appProperties.setRepositoryType("jpa");

    SongLibraryServiceImpl service = new SongLibraryServiceImpl(appProperties,
        songLibraryRepository, locationService, songScanner, Integer.valueOf(100), eventPublisher);

    // Delete the setup .oos file so its later presence proves storeSongLibraryAndStatistics()
    // itself rewrote it, rather than it merely surviving from construction-time adoption.
    Files.delete(oosFile);

    service.storeSongLibraryAndStatistics();

    assertTrue(Files.exists(oosFile),
        "JPA mode should keep a filesystem (.oos) backup of the in-memory library up to date, so "
            + "this instance can be switched back to repositoryType: filesystem without losing data");
    assertTrue(Files.exists(tempDir.resolve(RootFolderEntity.CD_STATS)),
        "JPA mode should still write CDStats.TXT -- it's the persistent record a filesystem-mode "
            + "scan reads back via restoreSongStatisticsForRootPath() to carry num-plays forward "
            + "across a rescan, in case this instance is later switched back to filesystem");
  }

  // ─────────────────────────────────────────────────────────────────────────
  // getMusicBySearch -- propagating an artist match into that artist's albums/songs
  // ─────────────────────────────────────────────────────────────────────────

  private SongLibraryServiceImpl newSearchTestService(RootFolderEntity fixtureRoot)
      throws EntityDoesNotExistException {

    SongLibraryRepository songLibraryRepository = mock(SongLibraryRepository.class);
    LocationService locationService = mock(LocationService.class);
    SongScanner songScanner = mock(SongScanner.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    LocationEntity ownLocation = new LocationEntity(1, "Test Location", null, null, "hash");

    when(locationService.getOrCreateOwnLocation(null)).thenReturn(ownLocation);
    when(songLibraryRepository.loadAggregateRoot(1)).thenReturn(fixtureRoot);

    AppProperties appProperties = new AppProperties();
    appProperties.setDataDir("unused-for-this-test");
    appProperties.setMode("standalone");

    return new SongLibraryServiceImpl(appProperties, songLibraryRepository, locationService,
        songScanner, Integer.valueOf(100), eventPublisher);
  }

  private AlbumFolderEntity newAlbum(FolderEntity parent, String name, int id)
      throws EntityAlreadyExistsException {

    AlbumFolderEntity album = new AlbumFolderEntity(parent, name);
    album.setId(id);
    parent.addChildFolder(album);
    album.createCoverArtEntity();
    album.createMetadataEntity();
    album.getMetaData().setLoaded(true);
    return album;
  }

  private void addSong(AlbumFolderEntity album, String songName, String artistName,
      int trackNumber, int id) throws EntityAlreadyExistsException {

    SongFileEntity song = new SongFileEntity(album, songName + ".mp3");
    song.setId(id);
    song.setArtistName(artistName);
    song.setSongName(songName);
    song.setTrackNumber(trackNumber);
    song.setNumPlays(0);
    album.addChildSong(song);
  }

  /**
   * Builds a small library used to exercise search propagation:
   * <ul>
   * <li>"Artist One" directly owns a non-compilation "Solo Album" -- neither song's title matches
   * the searches below, so it only surfaces via artist propagation.</li>
   * <li>"Singles (Soundtracks)" and "Mission Impossible 2 (Soundtracks)" are compilations (no
   * owning artist folder) that each credit one track to "Chris Cornell" among unrelated
   * artists -- mirrors the real-world example that motivated this feature.</li>
   * <li>"Totally Unique Title" is a compilation whose own title directly matches a search term, to
   * verify a direct album match still pulls in its full (unrelated-artist) tracklist.</li>
   * </ul>
   */
  private RootFolderEntity buildSearchPropagationRoot() throws EntityAlreadyExistsException {

    RootFolderEntity root = new RootFolderEntity("/root");
    root.setId(1);

    GenreFolderEntity genre = new GenreFolderEntity(root, "Rock");
    genre.setId(2);
    root.addChildFolder(genre);

    ArtistFolderEntity soloArtist = new ArtistFolderEntity(genre, "Artist One");
    soloArtist.setId(10);
    genre.addChildFolder(soloArtist);

    AlbumFolderEntity soloAlbum = newAlbum(soloArtist, "Solo Album", 20);
    addSong(soloAlbum, "Track A", "Artist One", 1, 30);
    addSong(soloAlbum, "Track B", "Artist One", 2, 31);

    AlbumFolderEntity singles = newAlbum(genre, "Singles (Soundtracks)", 21);
    addSong(singles, "Would", "Alice In Chains", 1, 32);
    addSong(singles, "Seasons", "Chris Cornell", 2, 33);
    addSong(singles, "Breath", "Pearl Jam", 3, 34);

    AlbumFolderEntity missionImpossible2 = newAlbum(genre, "Mission Impossible 2 (Soundtracks)", 22);
    addSong(missionImpossible2, "Take A Look Around", "Limp Bizkit", 1, 35);
    addSong(missionImpossible2, "Mission 2000", "Chris Cornell", 2, 36);
    addSong(missionImpossible2, "I Disappear", "Metallica", 3, 37);

    AlbumFolderEntity uniqueTitleAlbum = newAlbum(genre, "Totally Unique Title", 23);
    addSong(uniqueTitleAlbum, "Random Song One", "Artist X", 1, 38);
    addSong(uniqueTitleAlbum, "Random Song Two", "Artist Y", 2, 39);

    root.initialize();
    return root;
  }

  private static Set<String> songNames(List<SongDto> songs) {
    return songs.stream().map(SongDto::songName).collect(Collectors.toSet());
  }

  private static Set<String> albumNames(List<AlbumDto> albums) {
    return albums.stream().map(AlbumDto::albumName).collect(Collectors.toSet());
  }

  @Test
  void getMusicBySearch_propagatesCompilationCredits_onlyForMatchingTracks() throws Exception {

    SongLibraryServiceImpl service = newSearchTestService(buildSearchPropagationRoot());

    SearchResultDto result = service.getMusicBySearch(1, "Cornell");

    assertEquals(Set.of("Chris Cornell"),
        result.artists().stream().map(a -> a.artistName()).collect(Collectors.toSet()));

    assertEquals(Set.of("Singles (Soundtracks)", "Mission Impossible 2 (Soundtracks)"),
        albumNames(result.albums()),
        "both of Chris Cornell's compilation credits should be pulled into Albums via Item 1");

    assertEquals(Set.of("Seasons", "Mission 2000"), songNames(result.songs()),
        "only the tracks actually credited to Chris Cornell should be pulled in from those "
            + "compilation albums, not their unrelated tracks");
  }

  @Test
  void getMusicBySearch_propagatesNonCompilationAlbum_withFullTracklist() throws Exception {

    SongLibraryServiceImpl service = newSearchTestService(buildSearchPropagationRoot());

    SearchResultDto result = service.getMusicBySearch(1, "Artist One");

    assertTrue(albumNames(result.albums()).contains("Solo Album"),
        "Artist One's own album should be pulled into Albums via Item 1");

    assertEquals(Set.of("Track A", "Track B"), songNames(result.songs()),
        "a non-compilation album pulled in via artist propagation should contribute its entire "
            + "tracklist, even though neither song title matches the search term");
  }

  @Test
  void getMusicBySearch_directAlbumTitleMatch_pullsInFullTracklistEvenIfCompilation()
      throws Exception {

    SongLibraryServiceImpl service = newSearchTestService(buildSearchPropagationRoot());

    SearchResultDto result = service.getMusicBySearch(1, "Unique");

    assertTrue(albumNames(result.albums()).contains("Totally Unique Title"));
    assertEquals(Set.of("Random Song One", "Random Song Two"), songNames(result.songs()),
        "a direct album title match should pull in its full tracklist regardless of compilation "
            + "status, unlike an album reached only via artist propagation");
  }

  @Test
  void getGenreMusicByPopularity_isUnaffectedByArtistPropagation() throws Exception {

    SongLibraryServiceImpl service = newSearchTestService(buildSearchPropagationRoot());

    SearchResultDto result = service.getGenreMusicByPopularity(1, "Rock");

    assertEquals(4, result.albums().size(),
        "genre browsing has no search term, so the artist-propagation logic (which only runs "
            + "when searchFor != null) must not alter its results");
    assertEquals(10, result.songs().size());
  }
}
