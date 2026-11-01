package com.djt.jukeanator_engine.domain.songqueue.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
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
import com.djt.jukeanator_engine.domain.songqueue.repository.SongQueueRepositoryFileSystemImpl;

/**
 * This location's Gen Z patrons keep "trolling" the jukebox by queuing as much Creed as they can.
 * {@link SongQueueServiceImpl} hardcodes a stricter version of Rule A and Rule B specifically for
 * the artist "Creed" -- twice {@code minimum-minutes-between-song-plays}, half
 * {@code maximum-consecutive-song-plays-by-artist} -- without changing those properties for
 * everyone else.
 */
class SongQueueCreedSpecialCaseTest {

  private static final Integer LOCATION_ID = 42;
  private static final String LOCAL = SongQueueService.LOCAL_USERNAME;

  private static final int CREED_ALBUM = 801;
  private static final int CREED_SONG_1 = 9301;
  private static final int CREED_SONG_2 = 9302;
  private static final int CREED_SONG_3 = 9303;

  private static final int OTHER_ALBUM = 901;
  private static final int OTHER_SONG_1 = 9401;
  private static final int OTHER_SONG_2 = 9402;
  private static final int OTHER_SONG_3 = 9403;
  private static final int OTHER_SONG_4 = 9404;

  @TempDir
  Path dataDir;

  private RootFolderEntity library;
  private SongLibraryService songLibraryService;

  @BeforeEach
  void setUp() throws Exception {
    library = buildLibrary();
    songLibraryService = mock(SongLibraryService.class);
    when(songLibraryService.getOwnLocationId()).thenReturn(LOCATION_ID);
    when(songLibraryService.getSongLibraryRoot(LOCATION_ID)).thenReturn(library);
  }

  @Test
  void creedsWaitBetweenPlays_isDoubled_whileEveryoneElsesIsUnchanged() {

    SongQueueServiceImpl queue = newService();

    // Creed plays, then is refused for twice the configured 60-minute default.
    queue.addSongToQueue(LOCATION_ID,
        new AddSongToQueueRequest(LOCAL, CREED_ALBUM, CREED_SONG_1, 1, false));
    queue.dequeueNextSong();
    assertEquals("has already been played in the last 120 min. Try again in 120 min",
        eligibility(queue, CREED_ALBUM, CREED_SONG_1));

    // Some other artist plays, and is refused only for the configured 60-minute default.
    queue.addSongToQueue(LOCATION_ID,
        new AddSongToQueueRequest(LOCAL, OTHER_ALBUM, OTHER_SONG_1, 1, false));
    queue.dequeueNextSong();
    assertEquals("has already been played in the last 60 min. Try again in 60 min",
        eligibility(queue, OTHER_ALBUM, OTHER_SONG_1));
  }

  @Test
  void creedsConsecutivePlayLimit_isHalved_whileEveryoneElsesIsUnchanged() {

    SongQueueServiceImpl queue = newService();

    // Default maximum-consecutive-song-plays-by-artist is 3 -- halved (and rounded down) for
    // Creed means a single Creed song already queued is enough to block a second one.
    queue.addSongToQueue(LOCATION_ID,
        new AddSongToQueueRequest(LOCAL, CREED_ALBUM, CREED_SONG_1, 1, false));
    assertEquals("the consecutive play count for 'Creed' has been exceeded",
        eligibility(queue, CREED_ALBUM, CREED_SONG_2));

    // Some other artist keeps the unmodified default of 3: the 3rd consecutive song is still
    // fine, only the 4th is refused.
    queue.addSongToQueue(LOCATION_ID,
        new AddSongToQueueRequest(LOCAL, OTHER_ALBUM, OTHER_SONG_1, 1, false));
    queue.addSongToQueue(LOCATION_ID,
        new AddSongToQueueRequest(LOCAL, OTHER_ALBUM, OTHER_SONG_2, 1, false));
    assertNull(eligibility(queue, OTHER_ALBUM, OTHER_SONG_3));
    queue.addSongToQueue(LOCATION_ID,
        new AddSongToQueueRequest(LOCAL, OTHER_ALBUM, OTHER_SONG_3, 1, false));
    assertEquals("the consecutive play count for 'Someone Else' has been exceeded",
        eligibility(queue, OTHER_ALBUM, OTHER_SONG_4));
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  private SongQueueServiceImpl newService() {

    BackgroundMusicService backgroundMusic = mock(BackgroundMusicService.class);
    when(backgroundMusic.isEnabled()).thenReturn(false);
    SongQueueProperties properties = new SongQueueProperties();
    properties.setResetQueueAtStartup(true);

    return new SongQueueServiceImpl(properties, songLibraryService, backgroundMusic, repository(),
        mock(ApplicationEventPublisher.class), java.util.Optional.empty());
  }

  private SongQueueRepositoryFileSystemImpl repository() {
    return new SongQueueRepositoryFileSystemImpl(dataDir.toAbsolutePath().toString(),
        songLibraryService);
  }

  private static String eligibility(SongQueueServiceImpl queue, int albumId, int songId) {
    return queue.isSongEligibleForQueue(LOCATION_ID, albumId, songId, 1);
  }

  private static RootFolderEntity buildLibrary() throws Exception {

    RootFolderEntity root = new RootFolderEntity("/fixture/creed-special-case");
    GenreFolderEntity genre = new GenreFolderEntity(root, "Rock");
    genre.setId(1);
    root.addChildFolder(genre);

    ArtistFolderEntity creed = artist(genre, 2, "Creed");
    AlbumFolderEntity creedAlbum = album(creed, CREED_ALBUM, "Human Clay");
    song(creedAlbum, CREED_SONG_1, "With Arms Wide Open", "Creed", 1);
    song(creedAlbum, CREED_SONG_2, "My Sacrifice", "Creed", 2);
    song(creedAlbum, CREED_SONG_3, "Bullets", "Creed", 3);

    ArtistFolderEntity someoneElse = artist(genre, 3, "Someone Else");
    AlbumFolderEntity otherAlbum = album(someoneElse, OTHER_ALBUM, "Other Album");
    song(otherAlbum, OTHER_SONG_1, "Song One", "Someone Else", 1);
    song(otherAlbum, OTHER_SONG_2, "Song Two", "Someone Else", 2);
    song(otherAlbum, OTHER_SONG_3, "Song Three", "Someone Else", 3);
    song(otherAlbum, OTHER_SONG_4, "Song Four", "Someone Else", 4);

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
