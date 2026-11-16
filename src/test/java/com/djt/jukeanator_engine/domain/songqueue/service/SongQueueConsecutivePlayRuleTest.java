package com.djt.jukeanator_engine.domain.songqueue.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
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
import com.djt.jukeanator_engine.domain.songqueue.dto.AddMultipleSongsToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.AddSongToQueueRequest;
import com.djt.jukeanator_engine.domain.songqueue.dto.SongIdentifier;
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepositoryFileSystemImpl;

/**
 * Rule B ({@code maximum-consecutive-song-plays-by-artist}, default 3) must judge only the run of
 * same-artist songs the candidate would join. A run elsewhere in the queue that is already over the
 * limit -- e.g. an album or playlist added without the eligibility check, or a queue restored at
 * startup -- must not block songs by other artists.
 */
class SongQueueConsecutivePlayRuleTest {

  private static final Integer LOCATION_ID = 42;
  private static final String LOCAL = SongQueueService.LOCAL_USERNAME;

  private static final int MATCHBOX_ALBUM = 701;
  private static final int MB_1 = 7001;
  private static final int MB_2 = 7002;
  private static final int MB_3 = 7003;
  private static final int MB_4 = 7004;
  private static final int MB_5 = 7005;

  private static final int JOURNEY_ALBUM = 702;
  private static final int J_1 = 7101;
  private static final int J_2 = 7102;
  private static final int J_3 = 7103;
  private static final int J_4 = 7104;

  private static final String MATCHBOX_EXCEEDED =
      "the consecutive play count for 'Matchbox 20' has been exceeded";
  private static final String JOURNEY_EXCEEDED =
      "the consecutive play count for 'Journey' has been exceeded";

  @TempDir
  Path dataDir;

  private SongLibraryService songLibraryService;

  @BeforeEach
  void setUp() throws Exception {
    RootFolderEntity library = buildLibrary();
    songLibraryService = mock(SongLibraryService.class);
    when(songLibraryService.getOwnLocationId()).thenReturn(LOCATION_ID);
    when(songLibraryService.getSongLibraryRoot(LOCATION_ID)).thenReturn(library);
  }

  @Test
  void overLimitRunOfAnotherArtist_doesNotBlockUnrelatedSong() {

    SongQueueServiceImpl queue = newService();

    // The kiosk's LOCAL user is not eligibility-checked, so this run of 4 can exist.
    add(queue, MATCHBOX_ALBUM, MB_1, 1);
    add(queue, MATCHBOX_ALBUM, MB_2, 1);
    add(queue, MATCHBOX_ALBUM, MB_3, 1);
    add(queue, MATCHBOX_ALBUM, MB_4, 1);

    assertNull(eligibility(queue, JOURNEY_ALBUM, J_1, 1));
  }

  @Test
  void overLimitRunAddedAsAlbumOrPlaylist_doesNotBlockUnrelatedSong() {

    SongQueueServiceImpl queue = newService();

    // Album adds / playlist loads with skipIneligibleSongs=false bypass the check.
    queue.addMultipleSongsToQueue(LOCATION_ID,
        new AddMultipleSongsToQueueRequest(LOCAL,
            List.of(id(MATCHBOX_ALBUM, MB_1), id(MATCHBOX_ALBUM, MB_2), id(MATCHBOX_ALBUM, MB_3),
                id(MATCHBOX_ALBUM, MB_4), id(MATCHBOX_ALBUM, MB_5)),
            1, false, null));
    assertEquals(5, queue.getQueuedSongs(LOCATION_ID).size());

    assertNull(eligibility(queue, JOURNEY_ALBUM, J_1, 1));
    add(queue, JOURNEY_ALBUM, J_1, 1);
    assertNull(eligibility(queue, JOURNEY_ALBUM, J_2, 1));
  }

  @Test
  void candidatesOwnRun_isStillLimited_andNamesTheCandidatesArtist() {

    SongQueueServiceImpl queue = newService();

    // An over-limit Matchbox 20 run ahead of the Journey songs.
    add(queue, MATCHBOX_ALBUM, MB_1, 1);
    add(queue, MATCHBOX_ALBUM, MB_2, 1);
    add(queue, MATCHBOX_ALBUM, MB_3, 1);
    add(queue, MATCHBOX_ALBUM, MB_4, 1);

    add(queue, JOURNEY_ALBUM, J_1, 1);
    add(queue, JOURNEY_ALBUM, J_2, 1);
    assertNull(eligibility(queue, JOURNEY_ALBUM, J_3, 1));
    add(queue, JOURNEY_ALBUM, J_3, 1);

    assertEquals(JOURNEY_EXCEEDED, eligibility(queue, JOURNEY_ALBUM, J_4, 1));
    // A Matchbox 20 song appended after the Journey run starts a new run of 1.
    assertNull(eligibility(queue, MATCHBOX_ALBUM, MB_5, 1));
  }

  @Test
  void runExtendingTheOverLimitRun_isRefused() {

    SongQueueServiceImpl queue = newService();

    add(queue, MATCHBOX_ALBUM, MB_1, 1);
    add(queue, MATCHBOX_ALBUM, MB_2, 1);
    add(queue, MATCHBOX_ALBUM, MB_3, 1);
    add(queue, MATCHBOX_ALBUM, MB_4, 1);

    assertEquals(MATCHBOX_EXCEEDED, eligibility(queue, MATCHBOX_ALBUM, MB_5, 1));
  }

  @Test
  void runIsCountedAcrossPlayHistoryAndCurrentlyPlayingSong() {

    SongQueueServiceImpl queue = newService();

    add(queue, JOURNEY_ALBUM, J_1, 1);
    queue.dequeueNextSong(); // J_1 playing
    add(queue, JOURNEY_ALBUM, J_2, 1);
    queue.dequeueNextSong(); // J_1 in history, J_2 playing

    assertNull(eligibility(queue, JOURNEY_ALBUM, J_3, 1));
    add(queue, JOURNEY_ALBUM, J_3, 1);
    assertEquals(JOURNEY_EXCEEDED, eligibility(queue, JOURNEY_ALBUM, J_4, 1));
  }

  @Test
  void artistsPlaysBeforeARestart_doNotCountAfterIt() {

    SongQueueServiceImpl queue = newService();

    // Three Journey songs play in a row: a fourth is refused while this instance runs.
    for (int songId : new int[] {J_1, J_2, J_3}) {
      add(queue, JOURNEY_ALBUM, songId, 1);
      queue.dequeueNextSong();
    }
    assertEquals(JOURNEY_EXCEEDED, eligibility(queue, JOURNEY_ALBUM, J_4, 1));

    // The jukebox restarts: only this instance's plays count, so Journey may play again.
    SongQueueServiceImpl restarted = newService();
    assertNull(eligibility(restarted, JOURNEY_ALBUM, J_4, 1));
  }

  @Test
  void higherPriorityCandidate_isMeasuredWhereItWouldBeInserted() {

    SongQueueServiceImpl queue = newService();

    // Queue: J_1(p2), J_2(p2), MB_1(p1). A p2 Journey song lands after J_2 -> run of 3.
    add(queue, JOURNEY_ALBUM, J_1, 2);
    add(queue, JOURNEY_ALBUM, J_2, 2);
    add(queue, MATCHBOX_ALBUM, MB_1, 1);
    assertNull(eligibility(queue, JOURNEY_ALBUM, J_3, 2));
    add(queue, JOURNEY_ALBUM, J_3, 2);

    // Another p2 Journey song would make it 4; a p1 one lands after MB_1 and is fine.
    assertEquals(JOURNEY_EXCEEDED, eligibility(queue, JOURNEY_ALBUM, J_4, 2));
    assertNull(eligibility(queue, JOURNEY_ALBUM, J_4, 1));
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  private SongQueueServiceImpl newService() {

    BackgroundMusicService backgroundMusic = mock(BackgroundMusicService.class);
    when(backgroundMusic.isEnabled()).thenReturn(false);
    SongQueueProperties properties = new SongQueueProperties();
    properties.setResetQueueAtStartup(true);

    return new SongQueueServiceImpl(properties, songLibraryService, backgroundMusic,
        new SongQueueRepositoryFileSystemImpl(dataDir.toAbsolutePath().toString(),
            songLibraryService),
        mock(ApplicationEventPublisher.class), Optional.empty());
  }

  private static void add(SongQueueServiceImpl queue, int albumId, int songId, int priority) {
    queue.addSongToQueue(LOCATION_ID,
        new AddSongToQueueRequest(LOCAL, albumId, songId, priority, false));
  }

  private static SongIdentifier id(int albumId, int songId) {
    return new SongIdentifier(LOCATION_ID, albumId, songId);
  }

  private static String eligibility(SongQueueServiceImpl queue, int albumId, int songId,
      int priority) {
    return queue.isSongEligibleForQueue(LOCATION_ID, albumId, songId, priority);
  }

  private static RootFolderEntity buildLibrary() throws Exception {

    RootFolderEntity root = new RootFolderEntity("/fixture/consecutive-play-rule");
    GenreFolderEntity genre = new GenreFolderEntity(root, "Rock");
    genre.setId(1);
    root.addChildFolder(genre);

    ArtistFolderEntity matchbox = artist(genre, 2, "Matchbox 20");
    AlbumFolderEntity matchboxAlbum = album(matchbox, MATCHBOX_ALBUM, "Yourself or Someone Like You");
    song(matchboxAlbum, MB_1, "Real World", "Matchbox 20", 1);
    song(matchboxAlbum, MB_2, "Long Day", "Matchbox 20", 2);
    song(matchboxAlbum, MB_3, "3AM", "Matchbox 20", 3);
    song(matchboxAlbum, MB_4, "Push", "Matchbox 20", 4);
    song(matchboxAlbum, MB_5, "Back 2 Good", "Matchbox 20", 5);

    ArtistFolderEntity journey = artist(genre, 3, "Journey");
    AlbumFolderEntity journeyAlbum = album(journey, JOURNEY_ALBUM, "Escape");
    song(journeyAlbum, J_1, "Don't Stop Believin'", "Journey", 1);
    song(journeyAlbum, J_2, "Stone in Love", "Journey", 2);
    song(journeyAlbum, J_3, "Who's Crying Now", "Journey", 3);
    song(journeyAlbum, J_4, "Open Arms", "Journey", 4);

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
