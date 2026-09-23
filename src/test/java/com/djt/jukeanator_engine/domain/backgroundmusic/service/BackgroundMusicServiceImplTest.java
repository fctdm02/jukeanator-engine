package com.djt.jukeanator_engine.domain.backgroundmusic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import com.djt.jukeanator_engine.domain.backgroundmusic.config.BackgroundMusicProperties;
import com.djt.jukeanator_engine.domain.backgroundmusic.exception.BackgroundMusicServiceException;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.BackgroundMusicSongEntity;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartAdditionReason;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartBackgroundMusicSongEntity;
import com.djt.jukeanator_engine.domain.backgroundmusic.repository.BackgroundMusicRepository;
import com.djt.jukeanator_engine.domain.backgroundmusic.repository.SmartBackgroundMusicRepository;
import com.djt.jukeanator_engine.domain.common.exception.EntityDoesNotExistException;
import com.djt.jukeanator_engine.domain.location.event.OwnLocationIdChangedEvent;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;

/**
 * Covers {@link BackgroundMusicServiceImpl#handleOwnLocationIdChangedEvent} against mocked
 * repositories and a mocked {@link SongLibraryService} (fast, deterministic, no Spring context
 * needed) -- same style as {@code FinancialLedgerServiceTest}.
 *
 * <p>The service loads its in-memory state from the repositories during construction, so each
 * test seeds {@code BackgroundMusic.TXT} with exactly the paths the mocked repository returns (so
 * startup reconciliation adds nothing), and stubs {@code smartBackgroundMusicRepository.exists()}
 * true (so startup never tries to regenerate the smart pool). The song library mock returns
 * {@code null} for the library root, so startup's best-effort identifier backfill is skipped and
 * the seeded identifiers survive construction unchanged.
 *
 * <p>The smart-addition pool sizing tests instead stub {@code exists()} false and a mocked library
 * root, so startup builds the smart pool, and assert on what it stores.
 *
 * <p>The play-cycle tests stub the library root only after construction (so selection resolves
 * songs by path) and drive {@code getNextSong()}/{@code getNextSmartAdditionSong()} followed by
 * {@code markSongQueued()}, the same sequence {@code SongQueueServiceImpl.autoPopulateQueue} uses.
 *
 * @author tmyers
 */
class BackgroundMusicServiceImplTest {

  private static final Integer PREVIOUS_LOCATION_ID = Integer.valueOf(7);
  private static final Integer CONFIRMED_LOCATION_ID = Integer.valueOf(42);
  private static final Integer OTHER_LOCATION_ID = Integer.valueOf(99);

  private static final String OWN_SONG_PATH = "C:\\Music\\Own.mp3";
  private static final String OTHER_SONG_PATH = "C:\\Music\\Other.mp3";
  private static final String UNIDENTIFIED_SONG_PATH = "C:\\Music\\Unidentified.mp3";

  @TempDir
  private Path dataDir;

  private BackgroundMusicProperties backgroundMusicProperties;
  private SongLibraryService songLibraryService;
  private BackgroundMusicRepository backgroundMusicRepository;
  private SmartBackgroundMusicRepository smartBackgroundMusicRepository;

  @BeforeEach
  void setUp() throws Exception {

    backgroundMusicProperties = new BackgroundMusicProperties();
    backgroundMusicProperties.setEnableBackgroundMusic(true);
    backgroundMusicProperties.setEnableSmartBackgroundMusicAdditions(false);

    songLibraryService = mock(SongLibraryService.class);
    backgroundMusicRepository = mock(BackgroundMusicRepository.class);
    smartBackgroundMusicRepository = mock(SmartBackgroundMusicRepository.class);

    when(songLibraryService.getOwnLocationId()).thenReturn(PREVIOUS_LOCATION_ID);
    when(smartBackgroundMusicRepository.exists()).thenReturn(true);
    when(smartBackgroundMusicRepository.loadAll()).thenReturn(List.of());

    Files.write(dataDir.resolve("BackgroundMusic.TXT"),
        List.of(OWN_SONG_PATH, OTHER_SONG_PATH, UNIDENTIFIED_SONG_PATH));
  }

  private BackgroundMusicServiceImpl newService() {

    BackgroundMusicServiceImpl service = new BackgroundMusicServiceImpl(dataDir.toString(),
        backgroundMusicProperties, songLibraryService, backgroundMusicRepository,
        smartBackgroundMusicRepository);
    // Ignore anything startup reconciliation stored -- only the handler's own writes matter here.
    clearInvocations(backgroundMusicRepository, smartBackgroundMusicRepository);
    return service;
  }

  private static BackgroundMusicSongEntity song(int persistentIdentity, String path,
      SongIdentifier songIdentifier) {

    BackgroundMusicSongEntity song =
        new BackgroundMusicSongEntity(Integer.valueOf(persistentIdentity), path);
    song.setSongIdentifier(songIdentifier);
    return song;
  }

  private static SmartBackgroundMusicSongEntity smartSong(int persistentIdentity, String path,
      SongIdentifier songIdentifier, SongIdentifier sourceSongIdentifier) {

    SmartBackgroundMusicSongEntity song = new SmartBackgroundMusicSongEntity(
        Integer.valueOf(persistentIdentity), path, "C:\\Music\\Source.mp3", Integer.valueOf(5),
        SmartAdditionReason.SAME_ARTIST);
    song.setSongIdentifier(songIdentifier);
    song.setSourceSongIdentifier(sourceSongIdentifier);
    return song;
  }

  // ── re-tagging ───────────────────────────────────────────────────────────

  @Test
  void handleOwnLocationIdChangedEvent_retagsOnlyIdentifiersUnderThePreviousId_andPersistsBoth() {

    BackgroundMusicSongEntity ownSong =
        song(1, OWN_SONG_PATH, new SongIdentifier(PREVIOUS_LOCATION_ID, 10, 11));
    BackgroundMusicSongEntity otherSong =
        song(2, OTHER_SONG_PATH, new SongIdentifier(OTHER_LOCATION_ID, 20, 21));
    BackgroundMusicSongEntity unidentifiedSong = song(3, UNIDENTIFIED_SONG_PATH, null);
    when(backgroundMusicRepository.loadAll())
        .thenReturn(List.of(ownSong, otherSong, unidentifiedSong));

    SmartBackgroundMusicSongEntity smartSong = smartSong(4, "C:\\Music\\Smart.mp3",
        new SongIdentifier(PREVIOUS_LOCATION_ID, 30, 31),
        new SongIdentifier(PREVIOUS_LOCATION_ID, 40, 41));
    when(smartBackgroundMusicRepository.loadAll()).thenReturn(List.of(smartSong));

    BackgroundMusicServiceImpl service = newService();

    service.handleOwnLocationIdChangedEvent(
        new OwnLocationIdChangedEvent(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID));

    assertEquals(new SongIdentifier(CONFIRMED_LOCATION_ID, 10, 11), ownSong.getSongIdentifier());
    assertEquals(new SongIdentifier(OTHER_LOCATION_ID, 20, 21), otherSong.getSongIdentifier(),
        "An identifier under an unrelated location should be left alone");
    assertNull(unidentifiedSong.getSongIdentifier(),
        "A song with no identifier yet should stay unidentified");

    assertEquals(new SongIdentifier(CONFIRMED_LOCATION_ID, 30, 31),
        smartSong.getSongIdentifier());
    assertEquals(new SongIdentifier(CONFIRMED_LOCATION_ID, 40, 41),
        smartSong.getSourceSongIdentifier());

    verify(backgroundMusicRepository, times(1)).storeAll(anyList());
    verify(smartBackgroundMusicRepository, times(1)).storeAll(anyList());
  }

  @Test
  void handleOwnLocationIdChangedEvent_retagsSmartSourceIdentifier_evenWhenSongIdentifierIsUnset() {

    when(backgroundMusicRepository.loadAll()).thenReturn(List.of());

    SmartBackgroundMusicSongEntity smartSong = smartSong(4, "C:\\Music\\Smart.mp3", null,
        new SongIdentifier(PREVIOUS_LOCATION_ID, 40, 41));
    when(smartBackgroundMusicRepository.loadAll()).thenReturn(List.of(smartSong));

    BackgroundMusicServiceImpl service = newService();

    service.handleOwnLocationIdChangedEvent(
        new OwnLocationIdChangedEvent(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID));

    assertNull(smartSong.getSongIdentifier());
    assertEquals(new SongIdentifier(CONFIRMED_LOCATION_ID, 40, 41),
        smartSong.getSourceSongIdentifier());
    verify(smartBackgroundMusicRepository, times(1)).storeAll(anyList());
  }

  // ── no-op cases ──────────────────────────────────────────────────────────

  @Test
  void handleOwnLocationIdChangedEvent_storesNothing_whenNoIdentifierIsUnderThePreviousId() {

    when(backgroundMusicRepository.loadAll()).thenReturn(
        List.of(song(2, OTHER_SONG_PATH, new SongIdentifier(OTHER_LOCATION_ID, 20, 21))));
    when(smartBackgroundMusicRepository.loadAll()).thenReturn(
        List.of(smartSong(4, "C:\\Music\\Smart.mp3",
            new SongIdentifier(OTHER_LOCATION_ID, 30, 31), null)));

    BackgroundMusicServiceImpl service = newService();

    service.handleOwnLocationIdChangedEvent(
        new OwnLocationIdChangedEvent(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID));

    verify(backgroundMusicRepository, never()).storeAll(anyList());
    verify(smartBackgroundMusicRepository, never()).storeAll(anyList());
  }

  @Test
  void handleOwnLocationIdChangedEvent_doesNothing_whenBackgroundMusicIsDisabled() {

    backgroundMusicProperties.setEnableBackgroundMusic(false);
    BackgroundMusicSongEntity ownSong =
        song(1, OWN_SONG_PATH, new SongIdentifier(PREVIOUS_LOCATION_ID, 10, 11));
    when(backgroundMusicRepository.loadAll()).thenReturn(List.of(ownSong));

    BackgroundMusicServiceImpl service = newService();

    service.handleOwnLocationIdChangedEvent(
        new OwnLocationIdChangedEvent(PREVIOUS_LOCATION_ID, CONFIRMED_LOCATION_ID));

    assertEquals(new SongIdentifier(PREVIOUS_LOCATION_ID, 10, 11), ownSong.getSongIdentifier());
    verify(backgroundMusicRepository, never()).storeAll(anyList());
    verify(smartBackgroundMusicRepository, never()).storeAll(anyList());
  }

  // ── smart-addition pool sizing ───────────────────────────────────────────

  private static final String SOURCE_GENRE = "Rock";
  private static final String FAVORITE_GENRE = "Pop";
  private static final String FAVORITE_ALBUM_PATH = "/music/Pop/FavArtist/FavAlbum";
  private static final int SOURCE_COUNT = 40;

  private final List<SongFileEntity> librarySongs = new ArrayList<>();
  private final List<AlbumFolderEntity> libraryAlbums = new ArrayList<>();
  private final Set<String> regularPaths = new HashSet<>();
  private int nextAlbumId = 1;
  private int nextSongId = 1;

  private AlbumFolderEntity album(String genreName, String albumPath) {

    GenreFolderEntity genre = mock(GenreFolderEntity.class);
    when(genre.getName()).thenReturn(genreName);
    AlbumFolderEntity album = mock(AlbumFolderEntity.class);
    when(album.getId()).thenReturn(Integer.valueOf(nextAlbumId++));
    when(album.getParentGenre()).thenReturn(genre);
    when(album.getNaturalIdentity()).thenReturn(albumPath);
    when(album.getChildSongs()).thenReturn(new ArrayList<>());
    libraryAlbums.add(album);
    return album;
  }

  private SongFileEntity librarySong(AlbumFolderEntity album, String artistName, String path,
      int numPlays) {

    SongFileEntity song = mock(SongFileEntity.class);
    when(song.getId()).thenReturn(Integer.valueOf(nextSongId++));
    when(song.getAlbum()).thenReturn(album);
    when(song.getArtistName()).thenReturn(artistName);
    when(song.getNaturalIdentity()).thenReturn(path);
    when(song.getNumPlays()).thenReturn(Integer.valueOf(numPlays));
    album.getChildSongs().add(song);
    librarySongs.add(song);
    return song;
  }

  private List<SmartBackgroundMusicSongEntity> buildSmartPool(int factor, double favoritePct,
      int genreSongCount, int favoriteSongCount, List<String> genreExclusions) throws Exception {
    return buildSmartPool(factor, favoritePct, genreSongCount, favoriteSongCount, 0,
        genreExclusions);
  }

  /**
   * Builds a {@value #SOURCE_COUNT}-song {@code BackgroundMusic.TXT}, every source in the same
   * genre (each on its own artist/album, alongside {@code sameAlbumSongCount} sibling tracks),
   * plus {@code genreSongCount} more popular songs in that genre and a favorite album of
   * {@code favoriteSongCount} songs, then constructs the service so startup builds the smart pool,
   * returning what it stored.
   */
  private List<SmartBackgroundMusicSongEntity> buildSmartPool(int factor, double favoritePct,
      int genreSongCount, int favoriteSongCount, int sameAlbumSongCount,
      List<String> genreExclusions) throws Exception {

    List<String> sourcePaths = new ArrayList<>();
    for (int i = 0; i < SOURCE_COUNT; i++) {
      String albumPath = "/music/Rock/SrcArtist" + i + "/SrcAlbum";
      AlbumFolderEntity sourceAlbum = album(SOURCE_GENRE, albumPath);
      String path = albumPath + "/src" + i + ".mp3";
      librarySong(sourceAlbum, "SrcArtist" + i, path, 1);
      sourcePaths.add(path);
      for (int j = 0; j < sameAlbumSongCount; j++) {
        librarySong(sourceAlbum, "SrcArtist" + i, albumPath + "/sibling" + j + ".mp3", 2);
      }
    }
    for (int i = 0; i < genreSongCount; i++) {
      librarySong(album(SOURCE_GENRE, "/music/Rock/GenreArtist" + i + "/GenreAlbum"),
          "GenreArtist" + i, "/music/Rock/GenreArtist" + i + "/GenreAlbum/g" + i + ".mp3",
          1000 - i);
    }
    AlbumFolderEntity favoriteAlbum = album(FAVORITE_GENRE, FAVORITE_ALBUM_PATH);
    for (int i = 0; i < favoriteSongCount; i++) {
      librarySong(favoriteAlbum, "FavArtist", FAVORITE_ALBUM_PATH + "/f" + i + ".mp3", 5);
    }

    return buildSmartPoolFromLibrary(sourcePaths, factor, favoritePct, genreExclusions);
  }

  /**
   * Writes {@code sourcePaths} as {@code BackgroundMusic.TXT} over the library already built via
   * {@link #album}/{@link #librarySong}, constructs the service so startup builds the smart pool,
   * and returns what it stored.
   */
  private List<SmartBackgroundMusicSongEntity> buildSmartPoolFromLibrary(List<String> sourcePaths,
      int factor, double favoritePct, List<String> genreExclusions) throws Exception {

    regularPaths.addAll(sourcePaths);

    RootFolderEntity root = mock(RootFolderEntity.class);
    when(root.getSongs()).thenReturn(librarySongs);
    when(root.getAllAlbums()).thenReturn(libraryAlbums);
    for (SongFileEntity song : librarySongs) {
      when(root.getSongByPath(song.getNaturalIdentity())).thenReturn(song);
    }
    when(songLibraryService.getSongLibraryRoot(PREVIOUS_LOCATION_ID)).thenReturn(root);

    Files.write(dataDir.resolve("BackgroundMusic.TXT"), sourcePaths);
    Files.write(dataDir.resolve("SmartBackgroundMusicAlbumInclusions.TXT"),
        List.of("Pop/FavArtist/FavAlbum"));
    Files.write(dataDir.resolve("SmartBackgroundMusicGenreExclusions.TXT"), genreExclusions);

    backgroundMusicProperties.setEnableSmartBackgroundMusicAdditions(true);
    backgroundMusicProperties.setSmartBackgroundMusicAdditionsFactor(factor);
    backgroundMusicProperties.setSmartBackgroundMusicFavoriteAlbumsPercentage(favoritePct);
    backgroundMusicProperties.setSmartBackgroundMusicMinPlays(0);
    when(backgroundMusicRepository.loadAll()).thenReturn(List.of());
    when(smartBackgroundMusicRepository.exists()).thenReturn(false);

    new BackgroundMusicServiceImpl(dataDir.toString(), backgroundMusicProperties,
        songLibraryService, backgroundMusicRepository, smartBackgroundMusicRepository);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SmartBackgroundMusicSongEntity>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(smartBackgroundMusicRepository).storeAll(captor.capture());
    List<SmartBackgroundMusicSongEntity> pool = captor.getValue();

    Set<String> distinctPaths = pool.stream().map(SmartBackgroundMusicSongEntity::getSongFilePath)
        .collect(Collectors.toSet());
    assertEquals(pool.size(), distinctPaths.size(), "No path should appear twice in the pool");
    return pool;
  }

  private static long countByReason(List<SmartBackgroundMusicSongEntity> pool,
      SmartAdditionReason reason) {
    return pool.stream().filter(song -> song.getReason() == reason).count();
  }

  @Test
  void smartPool_givesEverySourceAnAddition_whenAllSourcesShareOneGenre() throws Exception {

    // Regression: the top-10 popularity slice used to be taken before reserved paths were
    // removed, so after ~10 sources in one genre every later source found nothing.
    List<SmartBackgroundMusicSongEntity> pool = buildSmartPool(1, 0, 200, 0, List.of());

    assertEquals(SOURCE_COUNT, pool.size());
    assertEquals(SOURCE_COUNT,
        countByReason(pool, SmartAdditionReason.POPULAR_SONG_FROM_GENRE));
  }

  @Test
  void smartPool_favoriteAlbumsAreAShareOfSourceCountTimesFactor() throws Exception {

    List<SmartBackgroundMusicSongEntity> pool = buildSmartPool(1, 25, 200, 50, List.of());

    assertEquals(SOURCE_COUNT, pool.size());
    assertEquals(10, countByReason(pool, SmartAdditionReason.SONG_FROM_FAVORITE_ALBUM));
  }

  @Test
  void smartPool_backfillsWithSourceCandidates_whenFavoriteAlbumsAreShort() throws Exception {

    List<SmartBackgroundMusicSongEntity> pool = buildSmartPool(1, 25, 200, 3, List.of());

    assertEquals(SOURCE_COUNT, pool.size());
    assertEquals(3, countByReason(pool, SmartAdditionReason.SONG_FROM_FAVORITE_ALBUM));
  }

  @Test
  void smartPool_topsUpWithFavoriteAlbums_whenSourcesYieldNothing() throws Exception {

    List<SmartBackgroundMusicSongEntity> pool =
        buildSmartPool(1, 25, 200, 100, List.of(SOURCE_GENRE));

    assertEquals(SOURCE_COUNT, pool.size());
    assertEquals(SOURCE_COUNT, countByReason(pool, SmartAdditionReason.SONG_FROM_FAVORITE_ALBUM));
  }

  @Test
  void smartPool_honorsGenreExclusions_forSourcesAndCandidates() throws Exception {

    // Half the regular songs are in an excluded genre whose songs are also the most popular in the
    // library, so any leak of that genre into the pool would be picked up.
    List<String> sourcePaths = new ArrayList<>();
    for (String genreName : List.of(SOURCE_GENRE, "Blues")) {
      for (int i = 0; i < SOURCE_COUNT / 2; i++) {
        String albumPath = "/music/" + genreName + "/SrcArtist" + i + "/SrcAlbum";
        AlbumFolderEntity sourceAlbum = album(genreName, albumPath);
        String path = albumPath + "/src" + i + ".mp3";
        librarySong(sourceAlbum, genreName + "SrcArtist" + i, path, 1);
        sourcePaths.add(path);
        for (int j = 0; j < 10; j++) {
          librarySong(sourceAlbum, genreName + "SrcArtist" + i,
              albumPath + "/sibling" + j + ".mp3", 2);
        }
      }
      for (int i = 0; i < 100; i++) {
        String albumPath = "/music/" + genreName + "/GenreArtist" + i + "/GenreAlbum";
        librarySong(album(genreName, albumPath), genreName + "GenreArtist" + i,
            albumPath + "/g" + i + ".mp3", (SOURCE_GENRE.equals(genreName) ? 5000 : 1000) - i);
      }
    }

    // Exclusion matching is case-insensitive; no favorite albums, so nothing tops the pool up.
    List<SmartBackgroundMusicSongEntity> pool =
        buildSmartPoolFromLibrary(sourcePaths, 2, 0, List.of(SOURCE_GENRE.toLowerCase()));

    Map<String, SongFileEntity> songsByPath = new HashMap<>();
    for (SongFileEntity song : librarySongs) {
      songsByPath.put(song.getNaturalIdentity(), song);
    }

    // Only the non-excluded half of the regular songs contribute, each a full factor's worth.
    assertEquals(SOURCE_COUNT / 2 * 2, pool.size());
    for (SmartBackgroundMusicSongEntity song : pool) {
      assertEquals("Blues",
          songsByPath.get(song.getSourceSong()).getAlbum().getParentGenre().getName(),
          "a regular song in an excluded genre must not seed smart additions");
      assertEquals("Blues",
          songsByPath.get(song.getSongFilePath()).getAlbum().getParentGenre().getName(),
          "a smart addition must never come from an excluded genre");
    }
    assertEquals(SOURCE_COUNT / 2, countByReason(pool, SmartAdditionReason.SAME_ALBUM));
    assertEquals(SOURCE_COUNT / 2,
        countByReason(pool, SmartAdditionReason.POPULAR_SONG_FROM_GENRE));
  }

  @Test
  void smartPool_skipsFavoriteAlbumsInExcludedGenres() throws Exception {

    // The favorite album is in an excluded genre, so despite the 25 % share (and the top-up once
    // the sources are exhausted) none of its songs may enter the pool — the pool is made up
    // entirely of source-derived songs.
    List<SmartBackgroundMusicSongEntity> pool =
        buildSmartPool(1, 25, 200, 100, List.of(FAVORITE_GENRE.toLowerCase()));

    assertEquals(SOURCE_COUNT, pool.size());
    assertEquals(0, countByReason(pool, SmartAdditionReason.SONG_FROM_FAVORITE_ALBUM));
    assertEquals(SOURCE_COUNT,
        countByReason(pool, SmartAdditionReason.POPULAR_SONG_FROM_GENRE));
  }

  @Test
  void smartPool_yieldsNothing_whenBothSourceAndFavoriteGenresAreExcluded() throws Exception {

    // Sources yield nothing and the favorite-album top-up must not bypass the exclusion either.
    List<SmartBackgroundMusicSongEntity> pool = buildSmartPool(1, 25, 200, 100,
        List.of(SOURCE_GENRE.toLowerCase(), FAVORITE_GENRE.toLowerCase()));

    assertEquals(0, pool.size());
  }

  /**
   * Mirrors the same-artist/album slot split documented on
   * {@code BackgroundMusicServiceImpl#computeSmartCandidatesForSource}: factor 1 → 0, factor 2–3 →
   * 1, factor ≥ 4 → 25 % (rounded, at least 1).
   */
  private static int expectedSameArtistSlots(int factor) {

    if (factor == 1) {
      return 0;
    }
    if (factor <= 3) {
      return 1;
    }
    return Math.max(1, Math.round(factor * 0.25f));
  }

  /**
   * Verifies the size and composition of the smart pool against the settings, when the library
   * has enough candidates of every kind:
   * <ul>
   * <li>total = regular song count × factor</li>
   * <li>favorite-album songs = round(percentage × total)</li>
   * <li>the rest are source-derived, split per source into same-artist/album and popular-genre
   * songs by the factor's mix formula (the last source contributing only the remaining slots)</li>
   * <li>every source-derived song names a regular song as its source, never itself, and no source
   * contributes more than factor songs</li>
   * </ul>
   */
  @ParameterizedTest(name = "factor={0}, favoritePct={1}")
  @CsvSource({"1, 0", "1, 25", "2, 25", "3, 20", "3, 33", "4, 25", "5, 50"})
  void smartPool_matchesSettings_inSizeAndComposition(int factor, double favoritePct)
      throws Exception {

    List<SmartBackgroundMusicSongEntity> pool =
        buildSmartPool(factor, favoritePct, 200, 150, 10, List.of());

    int expectedTotal = SOURCE_COUNT * factor;
    int expectedFavorites = (int) Math.round(favoritePct / 100.0 * expectedTotal);
    int expectedSourceDerived = expectedTotal - expectedFavorites;
    int fullSources = expectedSourceDerived / factor;
    int remainingSlots = expectedSourceDerived % factor;
    int expectedSameArtist = fullSources * expectedSameArtistSlots(factor)
        + (remainingSlots > 0 ? expectedSameArtistSlots(remainingSlots) : 0);
    int expectedGenre = expectedSourceDerived - expectedSameArtist;

    assertEquals(expectedTotal, pool.size(), "total = regular song count x factor");
    assertEquals(expectedFavorites,
        countByReason(pool, SmartAdditionReason.SONG_FROM_FAVORITE_ALBUM),
        "favorite-album share of the total");
    assertEquals(expectedSameArtist, countByReason(pool, SmartAdditionReason.SAME_ALBUM)
        + countByReason(pool, SmartAdditionReason.SAME_ARTIST), "same-artist/album songs");
    assertEquals(expectedGenre, countByReason(pool, SmartAdditionReason.POPULAR_SONG_FROM_GENRE),
        "popular-genre songs");

    Map<String, Long> countBySource = new HashMap<>();
    for (SmartBackgroundMusicSongEntity song : pool) {
      if (song.getReason() == SmartAdditionReason.SONG_FROM_FAVORITE_ALBUM) {
        assertNull(song.getSourceSong(), "favorite-album songs have no source song");
        assertTrue(song.getSongFilePath().startsWith(FAVORITE_ALBUM_PATH + "/"));
        continue;
      }
      assertTrue(regularPaths.contains(song.getSourceSong()),
          "source-derived songs are seeded from a regular song");
      assertNotEquals(song.getSourceSong(), song.getSongFilePath(),
          "a song is never its own smart addition");
      countBySource.merge(song.getSourceSong(), 1L, Long::sum);
    }
    assertEquals(fullSources + (remainingSlots > 0 ? 1 : 0), countBySource.size(),
        "number of regular songs contributing smart additions");
    assertTrue(countBySource.values().stream().allMatch(count -> count <= factor),
        "no regular song contributes more than factor smart additions");
  }

  // ── play cycles ──────────────────────────────────────────────────────────

  private static final String UNRESOLVABLE_PATH = "/music/Gone/Deleted.mp3";

  private static List<String> cyclePaths(String prefix, int count) {

    List<String> paths = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      paths.add("/music/" + prefix + "/song" + i + ".mp3");
    }
    return paths;
  }

  /**
   * Stubs the song library root so every path in {@code resolvablePaths} resolves to a song whose
   * natural identity is that path, and every path in {@code unresolvablePaths} throws. Done after
   * construction, so startup's identifier backfill is skipped and resolution stays path-based.
   */
  private void stubLibrary(List<String> resolvablePaths, List<String> unresolvablePaths)
      throws Exception {

    RootFolderEntity root = mock(RootFolderEntity.class);
    for (String path : resolvablePaths) {
      SongFileEntity song = mock(SongFileEntity.class);
      when(song.getNaturalIdentity()).thenReturn(path);
      when(root.getSongByPath(path)).thenReturn(song);
    }
    for (String path : unresolvablePaths) {
      when(root.getSongByPath(path))
          .thenThrow(new EntityDoesNotExistException("No song at " + path));
    }
    when(songLibraryService.getSongLibraryRoot(PREVIOUS_LOCATION_ID)).thenReturn(root);
  }

  private BackgroundMusicServiceImpl newRegularCycleService(List<String> paths)
      throws Exception {

    Files.write(dataDir.resolve("BackgroundMusic.TXT"), paths);
    List<BackgroundMusicSongEntity> songs = new ArrayList<>();
    for (int i = 0; i < paths.size(); i++) {
      songs.add(song(i + 1, paths.get(i), null));
    }
    when(backgroundMusicRepository.loadAll()).thenReturn(songs);
    return newService();
  }

  private BackgroundMusicServiceImpl newSmartCycleService(List<String> paths) throws Exception {

    Files.write(dataDir.resolve("BackgroundMusic.TXT"), List.of());
    List<SmartBackgroundMusicSongEntity> songs = new ArrayList<>();
    for (int i = 0; i < paths.size(); i++) {
      songs.add(smartSong(i + 1, paths.get(i), null, null));
    }
    when(smartBackgroundMusicRepository.loadAll()).thenReturn(songs);
    return newService();
  }

  @Test
  void regularCycle_picksEverySongOnceBeforeResetting() throws Exception {

    List<String> paths = cyclePaths("Regular", 5);
    BackgroundMusicServiceImpl service = newRegularCycleService(paths);
    stubLibrary(paths, List.of());

    for (int cycle = 0; cycle < 3; cycle++) {

      Set<String> picked = new HashSet<>();
      for (int i = 0; i < paths.size(); i++) {
        SongFileEntity song = service.getNextSong();
        assertTrue(picked.add(song.getNaturalIdentity()),
            "a song already played this cycle must not be picked again: "
                + song.getNaturalIdentity());
        service.markSongQueued(song);
      }
      assertEquals(new HashSet<>(paths), picked, "every song is picked once per cycle");
      verify(backgroundMusicRepository, times(cycle)).resetAllPlayedTimestamps(anyList());
    }
  }

  @Test
  void regularCycle_resets_whenOnlyUnresolvableSongsRemain() throws Exception {

    List<String> resolvable = cyclePaths("Regular", 2);
    List<String> paths = new ArrayList<>(resolvable);
    paths.add(UNRESOLVABLE_PATH);
    BackgroundMusicServiceImpl service = newRegularCycleService(paths);
    stubLibrary(resolvable, List.of(UNRESOLVABLE_PATH));

    Set<String> picked = new HashSet<>();
    for (int i = 0; i < resolvable.size(); i++) {
      SongFileEntity song = service.getNextSong();
      assertTrue(picked.add(song.getNaturalIdentity()));
      service.markSongQueued(song);
    }
    assertEquals(new HashSet<>(resolvable), picked);
    verify(backgroundMusicRepository, never()).resetAllPlayedTimestamps(anyList());

    // Only the unresolvable song is left: it is skipped, the cycle resets, and a resolvable song
    // is returned instead of the call failing.
    SongFileEntity next = service.getNextSong();

    assertTrue(resolvable.contains(next.getNaturalIdentity()));
    verify(backgroundMusicRepository, times(1)).resetAllPlayedTimestamps(anyList());
  }

  @Test
  void regularCycle_stillFails_whenEverySongIsUnresolvable() throws Exception {

    List<String> paths = cyclePaths("Regular", 3);
    BackgroundMusicServiceImpl service = newRegularCycleService(paths);
    stubLibrary(List.of(), paths);

    assertThrows(BackgroundMusicServiceException.class, service::getNextSong);
  }

  @Test
  void smartCycle_picksEverySongOnceBeforeResetting() throws Exception {

    List<String> paths = cyclePaths("Smart", 5);
    BackgroundMusicServiceImpl service = newSmartCycleService(paths);
    stubLibrary(paths, List.of());
    SongFileEntity coreSong = mock(SongFileEntity.class);

    for (int cycle = 0; cycle < 3; cycle++) {

      Set<String> picked = new HashSet<>();
      for (int i = 0; i < paths.size(); i++) {
        SongFileEntity song = service.getNextSmartAdditionSong(coreSong);
        assertNotNull(song);
        assertTrue(picked.add(song.getNaturalIdentity()),
            "a smart song already played this cycle must not be picked again: "
                + song.getNaturalIdentity());
        service.markSongQueued(song);
      }
      assertEquals(new HashSet<>(paths), picked, "every smart song is picked once per cycle");
      verify(smartBackgroundMusicRepository, times(cycle)).resetAllPlayedTimestamps(anyList());
    }
  }

  @Test
  void smartCycle_skipsUnresolvableSongs_andResetsWhenOnlyTheyRemain() throws Exception {

    List<String> resolvable = cyclePaths("Smart", 2);
    List<String> paths = new ArrayList<>(resolvable);
    paths.add(UNRESOLVABLE_PATH);
    BackgroundMusicServiceImpl service = newSmartCycleService(paths);
    stubLibrary(resolvable, List.of(UNRESOLVABLE_PATH));
    SongFileEntity coreSong = mock(SongFileEntity.class);

    // Whenever the unresolvable song is drawn, it is skipped in favor of another candidate rather
    // than ending the call with no song.
    Set<String> picked = new HashSet<>();
    for (int i = 0; i < resolvable.size(); i++) {
      SongFileEntity song = service.getNextSmartAdditionSong(coreSong);
      assertNotNull(song);
      assertTrue(picked.add(song.getNaturalIdentity()));
      service.markSongQueued(song);
    }
    assertEquals(new HashSet<>(resolvable), picked);
    verify(smartBackgroundMusicRepository, never()).resetAllPlayedTimestamps(anyList());

    SongFileEntity next = service.getNextSmartAdditionSong(coreSong);

    assertNotNull(next);
    assertTrue(resolvable.contains(next.getNaturalIdentity()));
    verify(smartBackgroundMusicRepository, times(1)).resetAllPlayedTimestamps(anyList());
  }

  @Test
  void smartCycle_returnsNull_whenEverySongIsUnresolvable() throws Exception {

    List<String> paths = cyclePaths("Smart", 3);
    BackgroundMusicServiceImpl service = newSmartCycleService(paths);
    stubLibrary(List.of(), paths);

    assertNull(service.getNextSmartAdditionSong(mock(SongFileEntity.class)));
  }
}
