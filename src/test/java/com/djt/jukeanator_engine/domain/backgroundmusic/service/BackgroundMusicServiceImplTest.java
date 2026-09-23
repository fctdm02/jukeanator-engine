package com.djt.jukeanator_engine.domain.backgroundmusic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.djt.jukeanator_engine.domain.backgroundmusic.config.BackgroundMusicProperties;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.BackgroundMusicSongEntity;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartAdditionReason;
import com.djt.jukeanator_engine.domain.backgroundmusic.model.SmartBackgroundMusicSongEntity;
import com.djt.jukeanator_engine.domain.backgroundmusic.repository.BackgroundMusicRepository;
import com.djt.jukeanator_engine.domain.backgroundmusic.repository.SmartBackgroundMusicRepository;
import com.djt.jukeanator_engine.domain.location.event.OwnLocationIdChangedEvent;
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
}
