package com.djt.jukeanator_engine.domain.songqueue.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import com.djt.jukeanator_engine.domain.backgroundmusic.service.BackgroundMusicService;
import com.djt.jukeanator_engine.domain.songlibrary.model.AlbumFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.ArtistFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.GenreFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.RootFolderEntity;
import com.djt.jukeanator_engine.domain.songlibrary.model.SongFileEntity;
import com.djt.jukeanator_engine.domain.songlibrary.service.SongLibraryService;
import com.djt.jukeanator_engine.domain.songqueue.config.SongQueueProperties;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.model.RecentSongPlays;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueEntryEntity;
import com.djt.jukeanator_engine.domain.songqueue.model.SongQueueRootEntity;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepositoryFileSystemImpl;

/**
 * Rule A of {@link SongQueueServiceImpl#isSongEligibleForQueue}: a song is never queued twice, and
 * never replayed within {@code minimum-minutes-between-song-plays} of when it last started
 * playing -- measured from the play, not from when it was queued, and remembered across a
 * restart. Runs against a small in-memory library and real filesystem storage in a temp dir.
 */
class SongQueueMinimumTimeBetweenPlaysTest {

  private static final Integer LOCATION_ID = 42;
  private static final String LOCAL = SongQueueService.LOCAL_USERNAME;

  /** "Song A" by Artist One, on Artist One's own album. */
  private static final int ALBUM_ONE = 501;
  private static final int SONG_A = 9001;
  private static final int SONG_B = 9002;
  /** The same "Song A" by Artist One, on a compilation. */
  private static final int COMPILATION = 601;
  private static final int SONG_A_ON_COMPILATION = 9101;
  /** A different song that happens to share the title. */
  private static final int OTHER_ALBUM = 701;
  private static final int OTHER_SONG_A = 9201;

  @TempDir
  Path dataDir;

  private RootFolderEntity library;
  private SongLibraryService songLibraryService;
  private Path recentPlaysFile;

  @BeforeEach
  void setUp() throws Exception {
    library = buildLibrary();
    songLibraryService = mock(SongLibraryService.class);
    when(songLibraryService.getOwnLocationId()).thenReturn(LOCATION_ID);
    when(songLibraryService.getSongLibraryRoot(LOCATION_ID)).thenReturn(library);
    recentPlaysFile = dataDir.resolve("JukeANator_RecentSongPlays.json");
  }

  @Test
  void aSongWaitingInTheQueue_isRefused_howeverLongItHasWaited() throws Exception {

    // The kiosk's situation: reset-queue-at-startup is false, and the queue reloaded at startup
    // still holds a song queued three hours ago.
    SongQueueRootEntity persisted =
        new SongQueueRootEntity(SongQueueRootEntity.SONG_QUEUE_FILENAME);
    SongQueueEntryEntity waiting =
        persisted.addSongToQueue(LOCAL, library.getSongById(ALBUM_ONE, SONG_A), 1);
    waiting.setQueuedAtTime(Instant.now().minus(Duration.ofHours(3)));
    repository().storeAggregateRoot(persisted);

    SongQueueServiceImpl queue = newService(false);

    assertEquals("is already in the queue", eligibility(queue, ALBUM_ONE, SONG_A));
    assertEquals("is already in the queue", eligibility(queue, COMPILATION, SONG_A_ON_COMPILATION),
        "the same song on another album is the same song");
    assertNull(eligibility(queue, OTHER_ALBUM, OTHER_SONG_A),
        "a different artist's song with the same title is a different song");
    assertNull(eligibility(queue, ALBUM_ONE, SONG_B));
  }

  @Test
  void aSongThatJustPlayed_isRefusedForTheMinimumTime_evenAfterARestart() {

    SongQueueServiceImpl queue = newService(true);
    queue.addSongToQueue(LOCATION_ID, new AddSongToQueueRequest(LOCAL, ALBUM_ONE, SONG_A, 1, false));
    assertEquals("is already in the queue", eligibility(queue, ALBUM_ONE, SONG_A));

    // It starts playing: no longer queued, but played moments ago.
    queue.dequeueNextSong();
    assertTrue(queue.getQueuedSongs(LOCATION_ID).isEmpty());
    String reason = eligibility(queue, ALBUM_ONE, SONG_A);
    assertEquals("has already been played in the last 60 min. Try again in 60 min", reason);
    assertEquals(reason, eligibility(queue, COMPILATION, SONG_A_ON_COMPILATION));
    assertNull(eligibility(queue, OTHER_ALBUM, OTHER_SONG_A));

    // The jukebox restarts: the play is remembered.
    assertTrue(Files.isRegularFile(recentPlaysFile));
    SongQueueServiceImpl restarted = newService(true);
    assertEquals(reason, eligibility(restarted, ALBUM_ONE, SONG_A));
  }

  @Test
  void aPlayOlderThanTheMinimumTime_isForgotten() {

    RecentSongPlays plays = new RecentSongPlays(Duration.ofMinutes(60), recentPlaysFile);
    SongFileEntity songA = song(ALBUM_ONE, SONG_A);
    Instant playedAt = Instant.now().minus(Duration.ofMinutes(61));
    plays.recordPlay(songA, playedAt);

    // Still within the window 59 minutes after it played...
    assertEquals(playedAt.toEpochMilli(), plays
        .lastPlayedWithinWindow(songA, playedAt.plus(Duration.ofMinutes(59))).orElseThrow()
        .toEpochMilli());
    // ...and forgotten once the window has passed.
    assertTrue(plays.lastPlayedWithinWindow(songA, Instant.now()).isEmpty());
    assertTrue(new RecentSongPlays(Duration.ofMinutes(60), recentPlaysFile)
        .lastPlayedWithinWindow(songA, Instant.now()).isEmpty());
  }

  @Test
  void anUnreadablePlaysFile_startsEmpty_ratherThanBreakingTheQueue() throws Exception {

    Files.writeString(recentPlaysFile, "not json");

    SongQueueServiceImpl queue = newService(true);

    assertNull(eligibility(queue, ALBUM_ONE, SONG_A));
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  private SongQueueServiceImpl newService(boolean resetQueueAtStartup) {

    BackgroundMusicService backgroundMusic = mock(BackgroundMusicService.class);
    when(backgroundMusic.isEnabled()).thenReturn(false);
    SongQueueProperties properties = new SongQueueProperties();
    properties.setResetQueueAtStartup(resetQueueAtStartup);

    return new SongQueueServiceImpl(properties, songLibraryService, backgroundMusic, repository(),
        mock(ApplicationEventPublisher.class), Optional.empty(), recentPlaysFile);
  }

  private SongQueueRepositoryFileSystemImpl repository() {
    return new SongQueueRepositoryFileSystemImpl(dataDir.toAbsolutePath().toString(),
        songLibraryService);
  }

  private static String eligibility(SongQueueServiceImpl queue, int albumId, int songId) {
    return queue.isSongEligibleForQueue(LOCATION_ID, albumId, songId, 1);
  }

  private SongFileEntity song(int albumId, int songId) {
    try {
      return library.getSongById(albumId, songId);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static RootFolderEntity buildLibrary() throws Exception {

    RootFolderEntity root = new RootFolderEntity("/fixture/minimum-time-between-plays");
    GenreFolderEntity genre = new GenreFolderEntity(root, "Rock");
    genre.setId(1);
    root.addChildFolder(genre);

    ArtistFolderEntity artistOne = artist(genre, 2, "Artist One");
    AlbumFolderEntity albumOne = album(artistOne, ALBUM_ONE, "Album One");
    song(albumOne, SONG_A, "Song A", "Artist One", 1);
    song(albumOne, SONG_B, "Song B", "Artist One", 2);

    ArtistFolderEntity compilations = artist(genre, 3, "Compilations");
    AlbumFolderEntity compilation = album(compilations, COMPILATION, "Big Hits");
    song(compilation, SONG_A_ON_COMPILATION, "Song A", "Artist One", 1);

    ArtistFolderEntity someoneElse = artist(genre, 4, "Someone Else");
    AlbumFolderEntity otherAlbum = album(someoneElse, OTHER_ALBUM, "Other Album");
    song(otherAlbum, OTHER_SONG_A, "Song A", "Someone Else", 1);

    root.initialize();
    return root;
  }

  private static ArtistFolderEntity artist(GenreFolderEntity genre, int id, String name)
      throws Exception {
    ArtistFolderEntity artist = new ArtistFolderEntity(genre, name);
    artist.setId(id);
    genre.addChildFolder(artist);
    return artist;
  }

  private static AlbumFolderEntity album(ArtistFolderEntity artist, int id, String name)
      throws Exception {
    AlbumFolderEntity album = new AlbumFolderEntity(artist, name);
    album.setId(id);
    album.createCoverArtEntity();
    artist.addChildFolder(album);
    return album;
  }

  private static void song(AlbumFolderEntity album, int id, String name, String artistName,
      int trackNumber) throws Exception {
    SongFileEntity song = new SongFileEntity(album, trackNumber + " - " + name + ".mp3");
    song.setId(id);
    song.setArtistName(artistName);
    song.setSongName(name);
    song.setTrackNumber(trackNumber);
    song.setNumPlays(0);
    album.addChildSong(song);
  }
}
